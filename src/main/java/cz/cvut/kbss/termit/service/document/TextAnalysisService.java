/*
 * TermIt
 * Copyright (C) 2023 Czech Technical University in Prague
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.service.document;

import cz.cvut.kbss.termit.dto.TextAnalysisInput;
import cz.cvut.kbss.termit.event.VocabularyFileTextAnalysisFinishedEvent;
import cz.cvut.kbss.termit.event.VocabularyTermDefinitionTextAnalysisFinishedEvent;
import cz.cvut.kbss.termit.exception.WebServiceIntegrationException;
import cz.cvut.kbss.termit.model.AbstractTerm;
import cz.cvut.kbss.termit.model.TextAnalysisRecord;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.persistence.dao.TextAnalysisRecordDao;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Utils;
import cz.cvut.kbss.termit.util.throttle.Throttle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class TextAnalysisService {

    private static final Logger LOG = LoggerFactory.getLogger(TextAnalysisService.class);

    private final RestTemplate restClient;

    private final Configuration config;

    private final DocumentManager documentManager;

    private final AnnotationGenerator annotationGenerator;

    private final TextAnalysisRecordDao recordDao;

    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public TextAnalysisService(RestTemplate restClient, Configuration config, DocumentManager documentManager,
                               AnnotationGenerator annotationGenerator, TextAnalysisRecordDao recordDao,
                               ApplicationEventPublisher eventPublisher) {
        this.restClient = restClient;
        this.config = config;
        this.documentManager = documentManager;
        this.annotationGenerator = annotationGenerator;
        this.recordDao = recordDao;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Passes the content of the specified file to the remote text analysis service, letting it find occurrences of
     * terms from the vocabularies specified by their repository contexts.
     * <p>
     * The analysis result is passed to the term occurrence generator.
     *
     * @param file               File whose content shall be analyzed
     * @param vocabularyContexts Identifiers of repository contexts containing vocabularies intended for text analysis
     */
    @Throttle(value = "{#file.getUri()}", name = "fileAnalysis")
    @Transactional
    public void analyzeFile(File file, Set<URI> vocabularyContexts) {
        Objects.requireNonNull(file);
        final TextAnalysisInput input = createAnalysisInput(file);
        input.setVocabularyContexts(vocabularyContexts);
        invokeTextAnalysisOnFile(file, input);
        LOG.debug("Text analysis finished for resource {}.", file.getUri());
        eventPublisher.publishEvent(new VocabularyFileTextAnalysisFinishedEvent(this, file));
    }

    private TextAnalysisInput createAnalysisInput(File file) {
        final TextAnalysisInput input = new TextAnalysisInput();
        input.setContent(documentManager.loadFileContent(file));
        final Optional<String> publicUrl = config.getRepository().getPublicUrl();
        URI repositoryUrl = URI.create(
                publicUrl.isEmpty() || publicUrl.get().isEmpty() ? config.getRepository().getUrl() : publicUrl.get()
        );
        input.setVocabularyRepository(repositoryUrl);
        input.setLanguage(config.getPersistence().getLanguage());
        input.setVocabularyRepositoryUserName(config.getRepository().getUsername());
        input.setVocabularyRepositoryPassword(config.getRepository().getPassword());
        return input;
    }

    private void invokeTextAnalysisOnFile(File file, TextAnalysisInput input) {
        try {
            final Optional<Resource> result = invokeTextAnalysisService(input);
            if (result.isEmpty()) {
                return;
            }
            documentManager.createBackup(file);
            try (final InputStream is = result.get().getInputStream()) {
                annotationGenerator.generateAnnotations(is, file);
            }
            storeTextAnalysisRecord(file, input);
        } catch (WebServiceIntegrationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new WebServiceIntegrationException("Text analysis invocation failed.", e);
        } catch (IOException e) {
            throw new WebServiceIntegrationException("Unable to read text analysis result from response.", e);
        }
    }

    private Optional<Resource> invokeTextAnalysisService(TextAnalysisInput input) {
        final String taUrl = config.getTextAnalysis().getUrl();
        if (taUrl == null || taUrl.isBlank()) {
            LOG.warn("Text analysis service URL not configured. Text analysis will not be invoked.");
            return Optional.empty();
        }
        final HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_XML_VALUE);
        LOG.debug("Invoking text analysis service at '{}' on input: {}", config.getTextAnalysis().getUrl(), input);
        final ResponseEntity<Resource> resp = restClient
                .exchange(config.getTextAnalysis().getUrl(), HttpMethod.POST,
                          new HttpEntity<>(input, headers), Resource.class);
        if (!resp.hasBody()) {
            throw new WebServiceIntegrationException("Text analysis service returned empty response.");
        }
        assert resp.getBody() != null;
        return Optional.of(resp.getBody());
    }

    private void storeTextAnalysisRecord(File file, TextAnalysisInput config) {
        LOG.trace("Creating record of text analysis event for file {}.", file);
        assert config.getVocabularyContexts() != null;

        final TextAnalysisRecord record = new TextAnalysisRecord(Utils.timestamp(), file);
        record.setVocabularies(new HashSet<>(config.getVocabularyContexts()));
        recordDao.persist(record);
    }

    /**
     * Gets the latest {@link TextAnalysisRecord} for the specified Resource.
     *
     * @param resource Analyzed Resource
     * @return Latest analysis record, if it exists
     */
    public Optional<TextAnalysisRecord> findLatestAnalysisRecord(cz.cvut.kbss.termit.model.resource.Resource resource) {
        return recordDao.findLatest(resource);
    }

    /**
     * Invokes text analysis on the specified term's definition.
     * <p>
     * The specified vocabulary context is used for analysis. Analysis results are stored as definitional term
     * occurrences.
     *
     * @param term Term whose definition is to be analyzed.
     */
    public void analyzeTermDefinition(AbstractTerm term, URI vocabularyContext) {
        Objects.requireNonNull(term);
        final String language = config.getPersistence().getLanguage();
        if (term.getDefinition() != null && term.getDefinition().contains(language)) {
            final TextAnalysisInput input = new TextAnalysisInput(term.getDefinition().get(language), language,
                                                                  URI.create(config.getRepository().getUrl()));
            input.addVocabularyContext(vocabularyContext);
            input.setVocabularyRepositoryUserName(config.getRepository().getUsername());
            input.setVocabularyRepositoryPassword(config.getRepository().getPassword());

            invokeTextAnalysisOnTerm(term, input);
            eventPublisher.publishEvent(new VocabularyTermDefinitionTextAnalysisFinishedEvent(this, term));
        }
    }

    private void invokeTextAnalysisOnTerm(AbstractTerm term, TextAnalysisInput input) {
        try {
            final Optional<Resource> result = invokeTextAnalysisService(input);
            if (result.isEmpty()) {
                return;
            }
            try (final InputStream is = result.get().getInputStream()) {
                annotationGenerator.generateAnnotations(is, term);
            }
        } catch (WebServiceIntegrationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new WebServiceIntegrationException("Text analysis invocation failed.", e);
        } catch (IOException e) {
            throw new WebServiceIntegrationException("Unable to read text analysis result from response.", e);
        }
    }
}
