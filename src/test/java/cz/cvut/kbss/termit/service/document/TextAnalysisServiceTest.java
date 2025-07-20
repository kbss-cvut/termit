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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.termit.dto.TextAnalysisInput;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.environment.PropertyMockingApplicationContextInitializer;
import cz.cvut.kbss.termit.event.FileTextAnalysisFinishedEvent;
import cz.cvut.kbss.termit.event.TermDefinitionTextAnalysisFinishedEvent;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.exception.UnsupportedTextAnalysisLanguageException;
import cz.cvut.kbss.termit.exception.WebServiceIntegrationException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.TextAnalysisRecord;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.persistence.dao.TextAnalysisRecordDao;
import cz.cvut.kbss.termit.persistence.dao.VocabularyDao;
import cz.cvut.kbss.termit.rest.handler.ErrorInfo;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withRequestConflict;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@ContextConfiguration(initializers = PropertyMockingApplicationContextInitializer.class)
class TextAnalysisServiceTest extends BaseServiceTestRunner {

    private static final String FILE_NAME = "tas-test.html";

    private static final String CONTENT =
            "<html><body><h1>Metropolitan plan</h1><p>Description of the metropolitan plan.</body></html>";

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private Configuration config;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private DocumentManager documentManager;

    @Mock
    private AnnotationGenerator annotationGeneratorMock;

    @Mock
    private TextAnalysisRecordDao textAnalysisRecordDao;

    @Mock
    private VocabularyDao vocabularyDao;

    private TextAnalysisService sut;

    private MockRestServiceServer mockServer;

    private ObjectMapper objectMapper;
    private DocumentManager documentManagerSpy;

    private Vocabulary vocabulary;

    private File file;

    @BeforeEach
    void setUp() throws Exception {
        this.mockServer = MockRestServiceServer.createServer(restTemplate);
        this.objectMapper = cz.cvut.kbss.termit.environment.Environment.getObjectMapper();
        this.vocabulary = Generator.generateVocabularyWithId();
        this.file = new File();
        file.setUri(Generator.generateUri());
        file.setLabel(FILE_NAME);
        file.setDocument(Generator.generateDocumentWithId());
        file.getDocument().setVocabulary(vocabulary.getUri());
        generateFile();
        this.documentManagerSpy = spy(documentManager);
        doCallRealMethod().when(documentManagerSpy).loadFileContent(any());
        doNothing().when(documentManagerSpy).createBackup(any());
        when(vocabularyDao.getReference(vocabulary.getUri())).thenReturn(vocabulary);
        this.sut = new TextAnalysisService(restTemplate, config, documentManagerSpy, annotationGeneratorMock,
                                           textAnalysisRecordDao, eventPublisher, vocabularyDao);
    }

    @Test
    void analyzeFileInvokesTextAnalysisServiceWithDocumentContent() {
        mockServer.expect(requestTo(config.getTextAnalysis().getUrl()))
                  .andExpect(method(HttpMethod.POST)).andExpect(content().string(containsString(CONTENT)))
                  .andRespond(withSuccess(CONTENT, MediaType.APPLICATION_XML));
        sut.analyzeFile(file, Collections.singleton(vocabulary.getUri()));
        mockServer.verify();
    }

    private void generateFile() throws IOException {
        final java.io.File dir = Files.createTempDirectory("termit").toFile();
        dir.deleteOnExit();
        config.getFile().setStorage(dir.getAbsolutePath());
        final java.io.File docDir = new java.io.File(
                dir.getAbsolutePath() + java.io.File.separator + file.getDirectoryName());
        Files.createDirectory(docDir.toPath());
        docDir.deleteOnExit();
        final java.io.File content = new java.io.File(
                docDir.getAbsolutePath() + java.io.File.separator + FILE_NAME);
        Files.write(content.toPath(), CONTENT.getBytes());
        content.deleteOnExit();
    }

    @Test
    void analyzeFilePassesRepositoryAndVocabularyContextToService() throws Exception {
        final TextAnalysisInput input = textAnalysisInput();
        mockServer.expect(requestTo(config.getTextAnalysis().getUrl()))
                  .andExpect(method(HttpMethod.POST))
                  .andExpect(content().string(objectMapper.writeValueAsString(input)))
                  .andRespond(withSuccess(CONTENT, MediaType.APPLICATION_XML));
        sut.analyzeFile(file, Collections.singleton(vocabulary.getUri()));
        mockServer.verify();
    }

    private TextAnalysisInput textAnalysisInput() {
        final TextAnalysisInput input = new TextAnalysisInput();
        input.setContent(CONTENT);
        input.addVocabularyContext(vocabulary.getUri());
        URI repositoryUrl = URI.create(
                config.getRepository().getPublicUrl()
                      .orElse(config.getRepository().getUrl())
        );
        input.setVocabularyRepository(repositoryUrl);
        input.setLanguage(config.getPersistence().getLanguage());
        input.setVocabularyRepositoryUserName(config.getRepository().getUsername());
        input.setVocabularyRepositoryPassword(config.getRepository().getPassword());
        return input;
    }

    @Test
    void analyzeFilePassesContentTypeAndAcceptHeadersToService() throws Exception {
        final TextAnalysisInput input = textAnalysisInput();
        mockServer.expect(requestTo(config.getTextAnalysis().getUrl()))
                  .andExpect(method(HttpMethod.POST))
                  .andExpect(content().string(objectMapper.writeValueAsString(input)))
                  .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                  .andExpect(header(HttpHeaders.ACCEPT,MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE))
                  .andRespond(withSuccess(CONTENT, MediaType.APPLICATION_XML));
        sut.analyzeFile(file, Collections.singleton(vocabulary.getUri()));
        mockServer.verify();
    }

    @Test
    void analyzeFilePassesRepositoryUsernameAndPasswordToServiceWhenProvided() throws Exception {
        final String username = "user";
        config.getRepository().setUsername(username);
        final String password = "password";
        config.getRepository().setPassword(password);
        final TextAnalysisInput input = textAnalysisInput();
        input.setVocabularyRepositoryUserName(username);
        input.setVocabularyRepositoryPassword(password);
        mockServer.expect(requestTo(config.getTextAnalysis().getUrl()))
                  .andExpect(method(HttpMethod.POST))
                  .andExpect(content().string(objectMapper.writeValueAsString(input)))
                  .andRespond(withSuccess(CONTENT, MediaType.APPLICATION_XML));
        sut.analyzeFile(file, Collections.singleton(vocabulary.getUri()));
        mockServer.verify();
    }

    @Test
    void analyzeFileThrowsWebServiceIntegrationExceptionOnError() throws Exception {
        final TextAnalysisInput input = textAnalysisInput();
        mockServer.expect(requestTo(config.getTextAnalysis().getUrl()))
                  .andExpect(method(HttpMethod.POST))
                  .andExpect(content().string(objectMapper.writeValueAsString(input)))
                  .andRespond(withServerError());
        assertThrows(WebServiceIntegrationException.class,
                     () -> sut.analyzeFile(file, Collections.singleton(vocabulary.getUri())));
        mockServer.verify();
    }

    @Test
    void analyzeFileInvokesAnnotationGeneratorWithResultFromTextAnalysisService() throws Exception {
        final TextAnalysisInput input = textAnalysisInput();
        mockServer.expect(requestTo(config.getTextAnalysis().getUrl()))
                  .andExpect(method(HttpMethod.POST))
                  .andExpect(content().string(objectMapper.writeValueAsString(input)))
                  .andRespond(withSuccess(CONTENT, MediaType.APPLICATION_XML));
        sut.analyzeFile(file, Collections.singleton(vocabulary.getUri()));
        mockServer.verify();
        final ArgumentCaptor<InputStream> captor = ArgumentCaptor.forClass(InputStream.class);
        verify(annotationGeneratorMock).generateAnnotations(captor.capture(), eq(file));
        final String result = new BufferedReader(new InputStreamReader(captor.getValue())).lines().collect(
                Collectors.joining("\n"));
        assertEquals(CONTENT, result);
    }

    @Test
    void analyzeFileThrowsNotFoundExceptionWhenFileCannotBeFound() {
        file.setLabel("unknown.html");
        final NotFoundException result = assertThrows(NotFoundException.class,
                                                      () -> sut.analyzeFile(file, Collections.singleton(
                                                              vocabulary.getUri())));
        assertThat(result.getMessage(), containsString("not found on file system"));
    }

    @Test
    void analyzeFileThrowsWebServiceIntegrationExceptionWhenRemoteServiceReturnsEmptyBody() throws Exception {
        final TextAnalysisInput input = textAnalysisInput();
        mockServer.expect(requestTo(config.getTextAnalysis().getUrl()))
                  .andExpect(method(HttpMethod.POST))
                  .andExpect(content().string(objectMapper.writeValueAsString(input)))
                  .andRespond(withSuccess());
        final WebServiceIntegrationException result = assertThrows(WebServiceIntegrationException.class,
                                                                   () -> sut.analyzeFile(file, Collections.singleton(
                                                                           vocabulary.getUri())));
        assertThat(result.getMessage(), containsString("empty response"));
        mockServer.verify();
    }

    @Test
    void analyzeFileCreatesFileBackupBeforeInvokingAnnotationGenerator() throws Exception {
        final TextAnalysisInput input = textAnalysisInput();
        mockServer.expect(requestTo(config.getTextAnalysis().getUrl()))
                  .andExpect(method(HttpMethod.POST))
                  .andExpect(content().string(objectMapper.writeValueAsString(input)))
                  .andRespond(withSuccess(CONTENT, MediaType.APPLICATION_XML));
        sut.analyzeFile(file, Collections.singleton(vocabulary.getUri()));
        mockServer.verify();
        final InOrder inOrder = Mockito.inOrder(documentManagerSpy, annotationGeneratorMock);
        inOrder.verify(documentManagerSpy).createBackup(file);
        inOrder.verify(annotationGeneratorMock).generateAnnotations(any(), eq(file));
    }

    @Test
    void analyzeFilePassesRepositoryAndSpecifiedVocabularyContextsToService() throws Exception {
        final Set<URI> vocabs = IntStream.range(0, 5).mapToObj(i -> Generator.generateUri())
                                         .collect(Collectors.toSet());
        final TextAnalysisInput expected = textAnalysisInput();
        expected.setVocabularyContexts(vocabs);
        mockServer.expect(requestTo(config.getTextAnalysis().getUrl()))
                  .andExpect(method(HttpMethod.POST))
                  .andExpect(content().string(objectMapper.writeValueAsString(expected)))
                  .andRespond(withSuccess(CONTENT, MediaType.APPLICATION_XML));
        sut.analyzeFile(file, vocabs);
        mockServer.verify();
    }

    @Test
    void analyzeFileBacksUpFileContentBeforeSavingNewAnalyzedContent() throws Exception {
        final TextAnalysisInput input = textAnalysisInput();
        mockServer.expect(requestTo(config.getTextAnalysis().getUrl()))
                  .andExpect(method(HttpMethod.POST))
                  .andExpect(content().string(objectMapper.writeValueAsString(input)))
                  .andRespond(withSuccess(CONTENT, MediaType.APPLICATION_XML));
        sut.analyzeFile(file, Collections.singleton(vocabulary.getUri()));
        mockServer.verify();
        final InOrder inOrder = Mockito.inOrder(documentManagerSpy, annotationGeneratorMock);
        inOrder.verify(documentManagerSpy).createBackup(file);
        inOrder.verify(annotationGeneratorMock).generateAnnotations(any(InputStream.class), eq(file));
    }

    @Test
    void analyzeFileCreatesTextAnalysisRecord() {
        file.setLanguage("cs");
        mockServer.expect(requestTo(config.getTextAnalysis().getUrl()))
                  .andExpect(method(HttpMethod.POST)).andExpect(content().string(containsString(CONTENT)))
                  .andRespond(withSuccess(CONTENT, MediaType.APPLICATION_XML));
        sut.analyzeFile(file, Collections.singleton(vocabulary.getUri()));
        final ArgumentCaptor<TextAnalysisRecord> captor = ArgumentCaptor.forClass(TextAnalysisRecord.class);
        verify(textAnalysisRecordDao).persist(captor.capture());
        assertEquals(file, captor.getValue().getAnalyzedResource());
        assertEquals(Collections.singleton(vocabulary.getUri()), captor.getValue().getVocabularies());
        assertEquals(file.getLanguage(), captor.getValue().getLanguage());
    }

    @Test
    void findLatestAnalysisRecordFindsLatestTextAnalysisRecordForResource() {
        final TextAnalysisRecord record = new TextAnalysisRecord(Utils.timestamp(), file, Environment.LANGUAGE);
        record.setVocabularies(Collections.singleton(vocabulary.getUri()));
        when(textAnalysisRecordDao.findLatest(file)).thenReturn(Optional.of(record));

        final Optional<TextAnalysisRecord> result = sut.findLatestAnalysisRecord(file);
        assertTrue(result.isPresent());
        assertEquals(record, result.get());
        verify(textAnalysisRecordDao).findLatest(file);
    }

    @Test
    void analyzeTermDefinitionInvokesTextAnalysisServiceWithTermDefinitionAsContentAndTermVocabularyAsVocabulary()
            throws Exception {
        final Term term = Generator.generateTermWithId();
        term.setVocabulary(vocabulary.getUri());
        final TextAnalysisInput input = textAnalysisInput();
        input.setContent(term.getDefinition().get(Environment.LANGUAGE));
        mockServer.expect(requestTo(config.getTextAnalysis().getUrl()))
                  .andExpect(method(HttpMethod.POST))
                  .andExpect(content().string(objectMapper.writeValueAsString(input)))
                  .andRespond(withSuccess(CONTENT, MediaType.APPLICATION_XML));

        sut.analyzeTermDefinition(term, vocabulary.getUri(), vocabulary.getPrimaryLanguage());
        mockServer.verify();
    }

    @Test
    void analyzeTermDefinitionInvokesAnnotationGeneratorWithResultFromTextAnalysisService() {
        final Term term = Generator.generateTermWithId();
        term.setVocabulary(vocabulary.getUri());
        mockServer.expect(requestTo(config.getTextAnalysis().getUrl()))
                  .andExpect(method(HttpMethod.POST))
                  .andExpect(content().string(containsString(term.getDefinition().get(Environment.LANGUAGE))))
                  .andRespond(withSuccess(CONTENT, MediaType.APPLICATION_XML));

        sut.analyzeTermDefinition(term, vocabulary.getUri(), vocabulary.getPrimaryLanguage());
        final ArgumentCaptor<InputStream> captor = ArgumentCaptor.forClass(InputStream.class);
        verify(annotationGeneratorMock).generateAnnotations(captor.capture(), eq(term));
        final String result = new BufferedReader(new InputStreamReader(captor.getValue())).lines().collect(
                Collectors.joining("\n"));
        assertEquals(CONTENT, result);
    }

    @Test
    void analyzeTermDefinitionDoesNotInvokeTextAnalysisServiceWhenDefinitionInConfiguredLanguageIsMissing() {
        final Term term = Generator.generateTermWithId();
        term.setVocabulary(vocabulary.getUri());
        term.setDefinition(MultilingualString.create("test value", "cs"));
        assertNotEquals(term.getDefinition().getLanguages(), Collections.singleton(Environment.LANGUAGE));
        sut.analyzeTermDefinition(term, vocabulary.getUri(), vocabulary.getPrimaryLanguage());
        mockServer.verify();
        verify(annotationGeneratorMock, never()).generateAnnotations(any(), any(Term.class));
    }

    @Test
    void analyzeTermDefinitionDoesNothingWhenTextAnalysisServiceUrlIsNotConfigured() {
        final Term term = Generator.generateTermWithId();
        term.setVocabulary(vocabulary.getUri());
        config.getTextAnalysis().setUrl(null);

        sut.analyzeTermDefinition(term, vocabulary.getUri(), vocabulary.getPrimaryLanguage());
        mockServer.verify();
        verify(annotationGeneratorMock, never()).generateAnnotations(any(), any(Term.class));
        verify(textAnalysisRecordDao, never()).persist(any());
    }

    @Test
    void analyzeTermDefinitionInvokesTextAnalysisServiceWithVocabularyRepositoryUsernameAndPassword()
            throws Exception {
        final Term term = Generator.generateTermWithId();
        term.setVocabulary(vocabulary.getUri());
        final TextAnalysisInput input = textAnalysisInput();
        input.setContent(term.getDefinition().get(Environment.LANGUAGE));
        final String username = "user";
        config.getRepository().setUsername(username);
        final String password = "password";
        config.getRepository().setPassword(password);
        input.setVocabularyRepositoryUserName(username);
        input.setVocabularyRepositoryPassword(password);
        mockServer.expect(requestTo(config.getTextAnalysis().getUrl()))
                  .andExpect(method(HttpMethod.POST))
                  .andExpect(content().string(objectMapper.writeValueAsString(input)))
                  .andRespond(withSuccess(CONTENT, MediaType.APPLICATION_XML));

        sut.analyzeTermDefinition(term, vocabulary.getUri(), vocabulary.getPrimaryLanguage());
        mockServer.verify();
    }

    @Test
    void analyzeFilePublishesAnalysisFinishedEvent() {
        mockServer.expect(requestTo(config.getTextAnalysis().getUrl()))
                  .andExpect(method(HttpMethod.POST)).andExpect(content().string(containsString(CONTENT)))
                  .andRespond(withSuccess(CONTENT, MediaType.APPLICATION_XML));
        sut.analyzeFile(file, Collections.singleton(vocabulary.getUri()));

        ArgumentCaptor<FileTextAnalysisFinishedEvent> eventCaptor = ArgumentCaptor.forClass(
                FileTextAnalysisFinishedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertNotNull(eventCaptor.getValue());
        assertEquals(file.getUri(), eventCaptor.getValue().getFileUri());
        assertEquals(vocabulary.getUri(), eventCaptor.getValue().getVocabularyIri());
    }

    @Test
    void analyzeTermDefinitionPublishesAnalysisFinishedEvent() throws JsonProcessingException {
        final Term term = Generator.generateTermWithId();
        term.setVocabulary(vocabulary.getUri());
        final TextAnalysisInput input = textAnalysisInput();
        input.setContent(term.getDefinition().get(Environment.LANGUAGE));
        mockServer.expect(requestTo(config.getTextAnalysis().getUrl()))
                  .andExpect(method(HttpMethod.POST))
                  .andExpect(content().string(objectMapper.writeValueAsString(input)))
                  .andRespond(withSuccess(CONTENT, MediaType.APPLICATION_XML));

        sut.analyzeTermDefinition(term, vocabulary.getUri(), vocabulary.getPrimaryLanguage());

        ArgumentCaptor<TermDefinitionTextAnalysisFinishedEvent> eventCaptor = ArgumentCaptor.forClass(
                TermDefinitionTextAnalysisFinishedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertNotNull(eventCaptor.getValue());
        assertEquals(term.getUri(), eventCaptor.getValue().getTermUri());
        assertEquals(vocabulary.getUri(), eventCaptor.getValue().getVocabularyIri());
    }

    @Test
    void analyzeFileSetsFileLanguageInTextAnalysisInvocationInput() {
        file.setLanguage("cs");
        mockServer.expect(requestTo(config.getTextAnalysis().getUrl()))
                  .andExpect(method(HttpMethod.POST))
                  .andExpect(jsonPath("$.language").value("cs"))
                  .andRespond(withSuccess(CONTENT, MediaType.APPLICATION_XML));
        sut.analyzeFile(file, Collections.singleton(vocabulary.getUri()));
        mockServer.verify();
    }

    @Test
    void analyzeFileUsesConfiguredPersistenceLanguageInTextAnalysisInvocationInputWhenFileLanguageIsNotSet() {
        file.setLanguage(null);
        mockServer.expect(requestTo(config.getTextAnalysis().getUrl()))
                  .andExpect(method(HttpMethod.POST))
                  .andExpect(jsonPath("$.language").value(Environment.LANGUAGE))
                  .andRespond(withSuccess(CONTENT, MediaType.APPLICATION_XML));
        sut.analyzeFile(file, Collections.singleton(vocabulary.getUri()));
        mockServer.verify();
    }

    @Test
    void analyzeFileThrowsUnsupportedLanguageExceptionWhenTextAnalysisInvocationReturnsConflictWithUnsupportedLanguageError()
            throws Exception {
        file.setLanguage("de");
        final ErrorInfo respBody = ErrorInfo.createWithMessage("No taggers for language 'de' available.",
                                                               "/annotace/annotate");
        mockServer.expect(requestTo(config.getTextAnalysis().getUrl()))
                  .andExpect(method(HttpMethod.POST))
                  .andRespond(withRequestConflict().body(objectMapper.writeValueAsString(respBody))
                                                   .contentType(MediaType.APPLICATION_JSON));

        final UnsupportedTextAnalysisLanguageException ex = assertThrows(UnsupportedTextAnalysisLanguageException.class,
                                                                         () -> sut.analyzeFile(file,
                                                                                               Collections.singleton(
                                                                                                       vocabulary.getUri())));
        assertEquals("error.annotation.file.unsupportedLanguage", ex.getMessageId());
    }

    @Test
    void supportsLanguageGetsListOfSupportedLanguagesFromTextAnalysisServiceAndChecksIfFileLanguageIsAmongThem() {
        file.setLanguage("cs");
        mockServer.expect(requestTo(config.getTextAnalysis().getLanguagesUrl()))
                  .andExpect(method(HttpMethod.GET))
                  .andRespond(withSuccess("[\"cs\", \"en\"]", MediaType.APPLICATION_JSON));
        assertTrue(sut.supportsLanguage(file));
        mockServer.verify();

        file.setLanguage("de");
        assertFalse(sut.supportsLanguage(file));
    }

    @Test
    void supportsLanguageReturnsTrueWhenTextAnalysisServiceLanguagesEndpointUrlIsNotConfigured() {
        String endpointUrl = config.getTextAnalysis().getLanguagesUrl();
        file.setLanguage(Environment.LANGUAGE);
        config.getTextAnalysis().setLanguagesUrl(null);
        assertTrue(sut.supportsLanguage(file));
        // Reset configuration state
        config.getTextAnalysis().setLanguagesUrl(endpointUrl);
    }

    @Test
    void supportsLanguageReturnsTrueWhenFileHasNoLanguageSet() {
        file.setLanguage(null);
        assertTrue(sut.supportsLanguage(file));
    }
}
