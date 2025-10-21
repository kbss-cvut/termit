/*
 * TermIt
 * Copyright (C) 2025 Czech Technical University in Prague
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
import cz.cvut.kbss.termit.event.FileTextAnalysisFinishedEvent;
import cz.cvut.kbss.termit.event.TermDefinitionTextAnalysisFinishedEvent;
import cz.cvut.kbss.termit.event.TextAnalysisFailedEvent;
import cz.cvut.kbss.termit.exception.TermItException;
import cz.cvut.kbss.termit.exception.UnsupportedTextAnalysisLanguageException;
import cz.cvut.kbss.termit.exception.WebServiceIntegrationException;
import cz.cvut.kbss.termit.model.AbstractTerm;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.TextAnalysisRecord;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.persistence.dao.TextAnalysisRecordDao;
import cz.cvut.kbss.termit.persistence.dao.VocabularyDao;
import cz.cvut.kbss.termit.rest.handler.ErrorInfo;
import cz.cvut.kbss.termit.service.document.backup.BackupReason;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Utils;
import cz.cvut.kbss.termit.util.throttle.Throttle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class TextAnalysisService {

    private static final Logger LOG = LoggerFactory.getLogger(TextAnalysisService.class);

    private final RestTemplate restClient;

    private final Configuration config;

    private final DocumentManager documentManager;

    private final AnnotationGenerator annotationGenerator;

    private final TextAnalysisRecordDao recordDao;

    private final ApplicationEventPublisher eventPublisher;

    private final VocabularyDao vocabularyDao;

    private Set<String> supportedLanguages;

    /**
     * Used for prefixing each term definition merged into a single input for text analysis.
     */
    private static final String TERM_DEFINITION_PREFIX = "<termdefinition id=\"";

    /**
     * Used for suffixing each term definition merged into a single input for text analysis.
     */
    private static final String TERM_DEFINITION_SUFFIX = "</termdefinition>";

    /**
     * Matches term definitions surrounded by {@link #TERM_DEFINITION_PREFIX} and {@link #TERM_DEFINITION_SUFFIX}.
     * <p>
     * Outputs two groups:
     * <ol>
     *     <li>Term URI</li>
     *     <li>Term definition</li>
     * </ol>
     */
    private static final Pattern TERM_DEFINITION_PATTERN = Pattern.compile(Pattern.quote(TERM_DEFINITION_PREFIX) + "([^\\\"]+)\" *\\>(.+?)" + Pattern.quote(TERM_DEFINITION_SUFFIX));

    @Autowired
    public TextAnalysisService(RestTemplate restClient, Configuration config, DocumentManager documentManager,
                               AnnotationGenerator annotationGenerator, TextAnalysisRecordDao recordDao,
                               ApplicationEventPublisher eventPublisher,
                               VocabularyDao vocabularyDao) {
        this.restClient = restClient;
        this.config = config;
        this.documentManager = documentManager;
        this.annotationGenerator = annotationGenerator;
        this.recordDao = recordDao;
        this.eventPublisher = eventPublisher;
        this.vocabularyDao = vocabularyDao;
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
        try {
            invokeTextAnalysisOnFile(file, input);
        } catch (TermItException e) {
            LOG.error("Text analysis failed: {}", e.getMessage());
            eventPublisher.publishEvent(new TextAnalysisFailedEvent(this, e, file));
            throw e;
        }
        LOG.debug("Text analysis finished for resource {}.", file.getUri());
        eventPublisher.publishEvent(new FileTextAnalysisFinishedEvent(this, file));
    }

    private TextAnalysisInput createAnalysisInput(File file) {
        final TextAnalysisInput input = new TextAnalysisInput();
        input.setContent(documentManager.loadFileContent(file));
        final Optional<String> publicUrl = config.getRepository().getPublicUrl();
        URI repositoryUrl = URI.create(
                publicUrl.isEmpty() || publicUrl.get().isEmpty() ? config.getRepository().getUrl() : publicUrl.get()
        );
        input.setVocabularyRepository(repositoryUrl);
        String vocabularyLanguage = vocabularyDao.getPrimaryLanguage(file.getDocument().getVocabulary());
        input.setLanguage(file.getLanguage() != null ? file.getLanguage() : vocabularyLanguage);
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
            documentManager.createBackup(file, BackupReason.TEXT_ANALYSIS);
            try (final InputStream is = result.get().getInputStream()) {
                annotationGenerator.generateAnnotations(is, file);
            }
            storeTextAnalysisRecord(file, input);
        } catch (WebServiceIntegrationException e) {
            throw e;
        } catch (HttpClientErrorException e) {
            throw handleTextAnalysisInvocationClientException(e, file);
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
        headers.addAll(HttpHeaders.ACCEPT, List.of(MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE));
        LOG.debug("Invoking text analysis service at '{}' on input: {}", taUrl, input);
        final ResponseEntity<Resource> resp = restClient.exchange(taUrl, HttpMethod.POST,
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

        final TextAnalysisRecord record = new TextAnalysisRecord(Utils.timestamp(), file, config.getLanguage());
        record.setVocabularies(new HashSet<>(config.getVocabularyContexts()));
        recordDao.persist(record);
    }

    private TermItException handleTextAnalysisInvocationClientException(HttpClientErrorException ex, Asset<?> asset) {
        if (ex.getStatusCode() == HttpStatus.CONFLICT) {
            final ErrorInfo errorInfo = ex.getResponseBodyAs(ErrorInfo.class);
            if (errorInfo != null && errorInfo.getMessage().contains("language")) {
                throw new UnsupportedTextAnalysisLanguageException(errorInfo.getMessage(),asset);
            }
        }
        throw new WebServiceIntegrationException("Text analysis invocation failed.", ex);
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
    public void analyzeTermDefinition(AbstractTerm term, URI vocabularyContext, String language) {
        Objects.requireNonNull(term);
        if (term.getDefinition() != null && term.getDefinition().contains(language)) {
            final TextAnalysisInput input = new TextAnalysisInput(term.getDefinition().get(language), language,
                                                                  URI.create(config.getRepository().getUrl()));
            input.addVocabularyContext(vocabularyContext);
            input.setVocabularyRepositoryUserName(config.getRepository().getUsername());
            input.setVocabularyRepositoryPassword(config.getRepository().getPassword());

            invokeTextAnalysisOnTerm(term, input);
            eventPublisher.publishEvent(new TermDefinitionTextAnalysisFinishedEvent(this, term));
        }
    }

    /**
     * Invokes text analysis with the specified input.
     * <p>
     * The analysis result is passed to the annotation generator with the given term.
     *
     * @param term  Term whose definition is to be analyzed.
     * @param input TextAnalysisInput containing the term definition and other necessary information.
     */
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
        } catch (HttpClientErrorException e) {
            throw handleTextAnalysisInvocationClientException(e, term);
        } catch (RuntimeException e) {
            throw new WebServiceIntegrationException("Text analysis invocation failed.", e);
        } catch (IOException e) {
            throw new WebServiceIntegrationException("Unable to read text analysis result from response.", e);
        }
    }

    /**
     * Combines the definitions of the given terms into a single string.
     * <p>
     * Each term definition is prefixed ({@link #TERM_DEFINITION_PREFIX}) and suffixed ({@link #TERM_DEFINITION_SUFFIX}).
     * The combined definitions are returned as a single string.
     * <p>
     * The termMap is populated with the terms and their URIs.
     *
     * @param terms   List of terms whose definitions are to be combined.
     * @param termMap Map to store the terms and their URIs.
     * @param language Language of the term definitions to include.
     * @return A string containing all combined term definitions.
     */
    private String combineTermDefinitions(List<AbstractTerm> terms, Map<URI, AbstractTerm> termMap, String language) {
        final StringBuilder definitions = new StringBuilder();
        terms.forEach(term -> {
            if (term.getDefinition() != null && term.getDefinition()
                                                    .contains(language)) {
                definitions.append(TERM_DEFINITION_PREFIX).append(term.getUri()).append("\">");
                definitions.append(term.getDefinition().get(language));
                definitions.append(TERM_DEFINITION_SUFFIX);
            }
            termMap.put(term.getUri(), term);
        });
        return definitions.toString();
    }

    /**
     * Analyzes term definitions for the given context-to-terms map.
     * <p>
     * Text analysis is invoked on all definitions merged for better efficiency.
     *
     * @param contextToTerms Map of vocabulary context URIs to lists of terms.
     * @param language       Language of the term definitions to analyze.
     */
    public void analyzeTermDefinitions(Map<URI, List<AbstractTerm>> contextToTerms, String language) {
        final var definitionsMap = new HashMap<URI, String>();
        // map of term URI to respective term
        // allows fast lookups by URI when generating annotations for each term
        final var termMap = new HashMap<URI, AbstractTerm>();
        // merge term definitions for each context and populate termMap
        contextToTerms.forEach((context, terms) -> {
            final String definitions = combineTermDefinitions(terms, termMap, language);
            if (!definitions.isEmpty()) {
                definitionsMap.put(context, definitions);
            }
        });

        // invoke text analysis and generate annotations
        definitionsMap.forEach((context, definitions) ->
                invokeTextAnalysisOnCombinedDefinitions(context, definitions, termMap, language)
        );
    }

    /**
     * Generates annotations for the combined term definitions.
     * <p>
     * Parses the result string to extract term definitions and generates annotations for each term.
     * Publishes an event for each term definition analysis completion.
     *
     * @param combinedResult The result of text analysis for combined term definitions.
     * @param termMap        Map of term URIs to terms.
     * @see #TERM_DEFINITION_PATTERN
     */
    private void generateAnnotationsForCombinedResult(String combinedResult, Map<URI, AbstractTerm> termMap) {
        final var matcher = TERM_DEFINITION_PATTERN.matcher(combinedResult);
        while (matcher.find()) {
            // skip if the pattern does not match the expected groups
            if (matcher.groupCount() != 2) {
                continue;
            }
            final String termid = matcher.group(1);
            final String termDefinition = matcher.group(2);
            final var term = termMap.get(URI.create(termid));
            annotationGenerator.generateAnnotations(new ByteArrayInputStream(termDefinition.getBytes(StandardCharsets.UTF_8)), term);
            eventPublisher.publishEvent(new TermDefinitionTextAnalysisFinishedEvent(this, term));
        }
    }

    /**
     * Invokes text analysis on the combined definitions for a given context.
     * <p>
     * Sends the combined definitions to the text analysis service and processes the result.
     * Generates annotations for the combined result.
     *
     * @param context     The vocabulary context URI.
     * @param definitions The combined term definitions.
     * @param termMap     Map of term URIs to terms.
     * @param language    Language of the term definitions to analyze.
     */
    private void invokeTextAnalysisOnCombinedDefinitions(URI context, String definitions,
                                                         Map<URI, AbstractTerm> termMap,
                                                         String language) {
        final TextAnalysisInput input = new TextAnalysisInput(
                definitions,
                language,
                URI.create(config.getRepository().getUrl())
        );
        input.addVocabularyContext(context);
        input.setVocabularyRepositoryUserName(config.getRepository().getUsername());
        input.setVocabularyRepositoryPassword(config.getRepository().getPassword());
        try {
            final Optional<Resource> result = invokeTextAnalysisService(input);
            if (result.isEmpty()) {
                return;
            }
            String resultString;
            // consume the input stream and create a string
            try (final InputStream is = result.get().getInputStream()) {
                resultString = StreamUtils.copyToString(is, StandardCharsets.UTF_8);
            }
            generateAnnotationsForCombinedResult(resultString, termMap);
        } catch (WebServiceIntegrationException e) {
            throw e;
        } catch (HttpClientErrorException e) {
            throw handleTextAnalysisInvocationClientException(e, null);
        } catch (RuntimeException e) {
            throw new WebServiceIntegrationException("Text analysis invocation failed.", e);
        } catch (IOException e) {
            throw new WebServiceIntegrationException("Unable to read text analysis result from response.", e);
        }
    }


    /**
     * Checks whether the text analysis service supports the language of the specified file.
     * <p>
     * If the text analysis service does not provide endpoint for getting supported languages (or it is not configured),
     * it is assumed that any language is supported.
     * <p>
     * If the file does not have language set, it is assumed that it is supported as well.
     *
     * @param file File to be analyzed
     * @return {@code true} if the file language is supported, {@code false} otherwise
     */
    public boolean supportsLanguage(File file) {
        Objects.requireNonNull(file);
        return file.getLanguage() == null || getSupportedLanguages().isEmpty() || getSupportedLanguages().contains(
                file.getLanguage());
    }

    private synchronized Set<String> getSupportedLanguages() {
        if (supportedLanguages != null) {
            return supportedLanguages;
        }
        final String languagesEndpointUrl = config.getTextAnalysis().getLanguagesUrl();
        if (languagesEndpointUrl == null || languagesEndpointUrl.isBlank()) {
            LOG.warn(
                    "Text analysis service languages endpoint URL not configured. Assuming any language is supported.");
            this.supportedLanguages = Set.of();
        } else {
            try {
                LOG.debug("Getting list of supported languages from text analysis service at '{}'.",
                          languagesEndpointUrl);
                ResponseEntity<Set<String>> response = restClient.exchange(languagesEndpointUrl, HttpMethod.GET, null,
                                                                           new ParameterizedTypeReference<>() {
                                                                           });
                this.supportedLanguages = response.getBody();
                if (supportedLanguages == null) {
                    this.supportedLanguages = Set.of();
                }
                LOG.trace("Text analysis supported languages: {}", supportedLanguages);
            } catch (RuntimeException e) {
                LOG.error("Unable to get list of supported languages from text analysis service at '{}'.",
                          languagesEndpointUrl, e);
                this.supportedLanguages = Set.of();
            }
        }
        return supportedLanguages;
    }
}
