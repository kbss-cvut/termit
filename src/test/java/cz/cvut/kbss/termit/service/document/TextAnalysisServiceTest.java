/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.service.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.termit.dto.TextAnalysisInput;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.environment.PropertyMockingApplicationContextInitializer;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.exception.WebServiceIntegrationException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.TextAnalysisRecord;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.persistence.dao.TextAnalysisRecordDao;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import cz.cvut.kbss.termit.util.Configuration;
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
import java.util.Date;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
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

    @Autowired
    private DocumentManager documentManager;

    @Mock
    private AnnotationGenerator annotationGeneratorMock;

    @Mock
    private TextAnalysisRecordDao textAnalysisRecordDao;

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
        generateFile();
        this.documentManagerSpy = spy(documentManager);
        doCallRealMethod().when(documentManagerSpy).loadFileContent(any());
        doNothing().when(documentManagerSpy).createBackup(any());
        this.sut = new TextAnalysisService(restTemplate, config, documentManagerSpy, annotationGeneratorMock,
                textAnalysisRecordDao);
    }

    @Test
    void analyzeFileInvokesTextAnalysisServiceWithDocumentContent() {
        final TextAnalysisInput input = new TextAnalysisInput();
        input.setContent(CONTENT);
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
        final java.io.File docDir = new java.io.File(dir.getAbsolutePath() + java.io.File.separator +
                file.getDirectoryName());
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
        return input;
    }

    @Test
    void analyzeFilePassesContentTypeAndAcceptHeadersToService() throws Exception {
        final TextAnalysisInput input = textAnalysisInput();
        mockServer.expect(requestTo(config.getTextAnalysis().getUrl()))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(objectMapper.writeValueAsString(input)))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_XML_VALUE))
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
                () -> sut.analyzeFile(file, Collections.singleton(vocabulary.getUri())));
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
                () -> sut.analyzeFile(file, Collections.singleton(vocabulary.getUri())));
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
        mockServer.expect(requestTo(config.getTextAnalysis().getUrl()))
                .andExpect(method(HttpMethod.POST)).andExpect(content().string(containsString(CONTENT)))
                .andRespond(withSuccess(CONTENT, MediaType.APPLICATION_XML));
        sut.analyzeFile(file, Collections.singleton(vocabulary.getUri()));
        final ArgumentCaptor<TextAnalysisRecord> captor = ArgumentCaptor.forClass(TextAnalysisRecord.class);
        verify(textAnalysisRecordDao).persist(captor.capture());
        assertEquals(file, captor.getValue().getAnalyzedResource());
        assertEquals(Collections.singleton(vocabulary.getUri()), captor.getValue().getVocabularies());
    }

    @Test
    void findLatestAnalysisRecordFindsLatestTextAnalysisRecordForResource() {
        final TextAnalysisRecord record = new TextAnalysisRecord(new Date(), file);
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

        sut.analyzeTermDefinition(term, vocabulary.getUri());
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

        sut.analyzeTermDefinition(term, vocabulary.getUri());
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
        sut.analyzeTermDefinition(term, vocabulary.getUri());
        mockServer.verify();
        verify(annotationGeneratorMock, never()).generateAnnotations(any(), any(Term.class));
    }
}
