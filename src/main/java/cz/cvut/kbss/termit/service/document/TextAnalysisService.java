/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If not, see
 * <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.service.document;

import cz.cvut.kbss.termit.dto.TextAnalysisInput;
import cz.cvut.kbss.termit.exception.WebServiceIntegrationException;
import cz.cvut.kbss.termit.model.TextAnalysisRecord;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.persistence.dao.TextAnalysisRecordDao;
import cz.cvut.kbss.termit.util.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.URI;
import java.util.*;

@Service
public class TextAnalysisService {

    private static final Logger LOG = LoggerFactory.getLogger(TextAnalysisService.class);

    private final RestTemplate restClient;

    private final Configuration config;

    private final DocumentManager documentManager;

    private final TextAnalysisRecordDao recordDao;

    @Autowired
    public TextAnalysisService(RestTemplate restClient, Configuration config, DocumentManager documentManager,
                               TextAnalysisRecordDao recordDao) {
        this.restClient = restClient;
        this.config = config;
        this.documentManager = documentManager;
        this.recordDao = recordDao;
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
    @Transactional
    public void analyzeFile(File file, Set<URI> vocabularyContexts) {
        Objects.requireNonNull(file);
        final TextAnalysisInput input = createAnalysisInput(file);
        input.setVocabularyContexts(vocabularyContexts);
        invokeTextAnalysisOnFile(file, input);
    }

    private TextAnalysisInput createAnalysisInput(File file) {
        final TextAnalysisInput input = new TextAnalysisInput();
        input.setContent(documentManager.loadFileContent(file));
        final Optional<String> publicUrl = config.getRepository().getPublicUrl();
        URI repositoryUrl = URI.create(
            !publicUrl.isPresent() || publicUrl.get().isEmpty() ? config.getRepository().getUrl() : publicUrl.get()
        );
        input.setVocabularyRepository(repositoryUrl);
        input.setLanguage(config.getPersistence().getLanguage());
        return input;
    }

    private void invokeTextAnalysisOnFile(File file, TextAnalysisInput input) {
        try {
            final Resource result = invokeTextAnalysisService(input);
            documentManager.createBackup(file);
            documentManager.saveFileContent(file, result.getInputStream());
            storeTextAnalysisRecord(file, input);
        } catch (WebServiceIntegrationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new WebServiceIntegrationException("Text analysis invocation failed.", e);
        } catch (IOException e) {
            throw new WebServiceIntegrationException("Unable to read text analysis result from response.", e);
        }
    }

    private Resource invokeTextAnalysisService(TextAnalysisInput input) {
        final HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_XML_VALUE);
        LOG.debug("Invoking text analysis service on input: {}", input);
        final ResponseEntity<Resource> resp = restClient
                .exchange(config.getTextAnalysis().getUrl(), HttpMethod.POST,
                        new HttpEntity<>(input, headers), Resource.class);
        if (!resp.hasBody()) {
            throw new WebServiceIntegrationException("Text analysis service returned empty response.");
        }
        assert resp.getBody() != null;
        return resp.getBody();
    }

    private void storeTextAnalysisRecord(File file, TextAnalysisInput config) {
        LOG.trace("Creating record of text analysis event for file {}.", file);
        assert config.getVocabularyContexts() != null;

        final TextAnalysisRecord record = new TextAnalysisRecord(new Date(), file);
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

}
