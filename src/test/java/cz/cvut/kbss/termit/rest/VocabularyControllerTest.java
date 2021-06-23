package cz.cvut.kbss.termit.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.VocabularyImportException;
import cz.cvut.kbss.termit.exception.VocabularyRemovalException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.validation.ValidationResult;
import cz.cvut.kbss.termit.rest.handler.ErrorInfo;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.VocabularyService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.Constants.QueryParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static cz.cvut.kbss.termit.environment.Generator.generateTerm;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class VocabularyControllerTest extends BaseControllerTestRunner {

    private static final String PATH = "/vocabularies";
    private static final String NAMESPACE =
            "http://onto.fel.cvut.cz/ontologies/termit/vocabularies/";
    private static final String FRAGMENT = "test";
    private static final URI VOCABULARY_URI = URI.create(NAMESPACE + FRAGMENT);

    @Mock
    private VocabularyService serviceMock;

    @Mock
    private IdentifierResolver idResolverMock;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Configuration configMock;

    @InjectMocks
    private VocabularyController sut;

    private User user;

    @BeforeEach
    void setUp() {
        super.setUp(sut);
        this.user = Generator.generateUserWithId();
    }

    @Test
    void getAllReturnsAllExistingVocabularies() throws Exception {
        final List<Vocabulary> vocabularies =
                IntStream.range(0, 5).mapToObj(i -> generateVocabulary())
                         .collect(Collectors.toList());
        when(serviceMock.findAll()).thenReturn(vocabularies);

        final MvcResult mvcResult =
                mockMvc.perform(get(PATH)).andExpect(status().isOk()).andReturn();
        final List<Vocabulary> result = readValue(mvcResult, new TypeReference<List<Vocabulary>>() {
        });
        assertEquals(vocabularies.size(), result.size());
        for (Vocabulary voc : vocabularies) {
            assertTrue(result.stream().anyMatch(v -> v.getUri().equals(voc.getUri())));
        }
    }

    private Vocabulary generateVocabulary() {
        return Generator.generateVocabularyWithId();
    }

    @Test
    void getAllReturnsLastModifiedHeader() throws Exception {
        final List<Vocabulary> vocabularies =
                IntStream.range(0, 5).mapToObj(i -> generateVocabulary())
                         .collect(Collectors.toList());
        when(serviceMock.findAll()).thenReturn(vocabularies);
        // Round to seconds
        final long lastModified = (System.currentTimeMillis() / 1000) * 1000;
        when(serviceMock.getLastModified()).thenReturn(lastModified);

        final MvcResult mvcResult =
                mockMvc.perform(get(PATH)).andExpect(status().isOk()).andReturn();
        final String lastModifiedHeader =
                mvcResult.getResponse().getHeader(HttpHeaders.LAST_MODIFIED);
        assertNotNull(lastModifiedHeader);
        ZonedDateTime zdt =
                ZonedDateTime.parse(lastModifiedHeader, DateTimeFormatter.RFC_1123_DATE_TIME);
        assertEquals(lastModified, zdt.toInstant().toEpochMilli());
    }

    @Test
    void getAllReturnsNotModifiedWhenLastModifiedDateIsBeforeIfModifiedSinceHeaderValue() throws Exception {
        // Round to seconds
        final long lastModified = (System.currentTimeMillis() - 60 * 1000);
        when(serviceMock.getLastModified()).thenReturn(lastModified);

        mockMvc.perform(
                get(PATH).header(HttpHeaders.IF_MODIFIED_SINCE,
                        DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now())))
               .andExpect(status().isNotModified());
        verify(serviceMock).getLastModified();
        verify(serviceMock, never()).findAll();
    }

    @Test
    void createVocabularyPersistsSpecifiedVocabulary() throws Exception {
        final Vocabulary vocabulary = Generator.generateVocabulary();
        vocabulary.setUri(Generator.generateUri());

        mockMvc.perform(
                post(PATH).content(toJson(vocabulary)).contentType(MediaType.APPLICATION_JSON_VALUE))
               .andExpect(status().isCreated());
        final ArgumentCaptor<Vocabulary> captor = ArgumentCaptor.forClass(Vocabulary.class);
        verify(serviceMock).persist(captor.capture());
        assertEquals(vocabulary.getUri(), captor.getValue().getUri());
    }

    @Test
    void createVocabularyReturnsResponseWithLocationHeader() throws Exception {
        final Vocabulary vocabulary = Generator.generateVocabulary();
        vocabulary.setUri(Generator.generateUri());
        final String fragment = IdentifierResolver.extractIdentifierFragment(vocabulary.getUri());

        final MvcResult mvcResult = mockMvc.perform(
                post(PATH).content(toJson(vocabulary)).contentType(MediaType.APPLICATION_JSON_VALUE))
                                           .andExpect(status().isCreated()).andReturn();
        verifyLocationEquals(PATH + "/" + fragment, mvcResult);
    }

    @Test
    void createVocabularyRunsImportWhenFileIsUploaded() throws Exception {
        final Vocabulary vocabulary = Generator.generateVocabulary();
        vocabulary.setUri(URI.create(NAMESPACE + FRAGMENT));
        when(serviceMock.importVocabulary(anyBoolean(),any(),any())).thenReturn(vocabulary);
        final MockMultipartFile upload = new MockMultipartFile("file", "test-glossary.ttl",
                Constants.Turtle.MEDIA_TYPE, Environment.loadFile("data/test-glossary.ttl"));
        final MvcResult mvcResult = mockMvc.perform(multipart(PATH + "/import").file(upload)
            .param("rename", "false"))
                .andExpect(status().isCreated())
                .andReturn();
        verifyLocationEquals(PATH + "/" + FRAGMENT, mvcResult);
        verify(serviceMock).importVocabulary(false, null, upload);
    }

    @Test
    void getByIdLoadsVocabularyFromRepository() throws Exception {
        final Vocabulary vocabulary = generateVocabulary();
        final String fragment = IdentifierResolver.extractIdentifierFragment(vocabulary.getUri());
        when(idResolverMock.resolveIdentifier(configMock.getNamespace().getVocabulary(), fragment))
                .thenReturn(vocabulary.getUri());
        when(serviceMock.findRequired(vocabulary.getUri())).thenReturn(vocabulary);

        final MvcResult mvcResult = mockMvc
                .perform(get(PATH + "/" + fragment).accept(MediaType.APPLICATION_JSON_VALUE))
                .andExpect(status().isOk()).andReturn();
        final Vocabulary result = readValue(mvcResult, Vocabulary.class);
        assertNotNull(result);
        assertEquals(vocabulary.getUri(), result.getUri());
        assertEquals(vocabulary.getLabel(), result.getLabel());
    }

    @Test
    void getByIdUsesSpecifiedNamespaceInsteadOfDefaultOneForResolvingIdentifier() throws Exception {
        final Vocabulary vocabulary = generateVocabulary();
        final String fragment =
                IdentifierResolver.extractIdentifierFragment(vocabulary.getUri()).substring(1);
        final String namespace = vocabulary.getUri().toString()
                                           .substring(0, vocabulary.getUri().toString().lastIndexOf('/'));
        when(idResolverMock.resolveIdentifier(namespace, fragment)).thenReturn(vocabulary.getUri());
        when(serviceMock.findRequired(vocabulary.getUri())).thenReturn(vocabulary);

        final MvcResult mvcResult = mockMvc.perform(
                get(PATH + "/" + fragment).accept(MediaType.APPLICATION_JSON_VALUE)
                                          .param(QueryParams.NAMESPACE, namespace))
                                           .andReturn();
        assertEquals(200, mvcResult.getResponse().getStatus());
        verify(idResolverMock).resolveIdentifier(namespace, fragment);
    }

    @Test
    void removeVocabularyReturns2xxForEmptyVocabulary() throws Exception {
        final Vocabulary vocabulary = Generator.generateVocabulary();
        vocabulary.setUri(Generator.generateUri());
        final String fragment = IdentifierResolver.extractIdentifierFragment(vocabulary.getUri());
        mockMvc.perform(
                delete(PATH + "/" + fragment))
               .andExpect(status().is2xxSuccessful()).andReturn();
    }

    @Test
    void removeVocabularyReturns4xxForNotRemovableVocabulary() throws Exception {
        Mockito.doThrow(
                new VocabularyRemovalException("Vocabulary cannot be removed. It contains terms."))
               .when(serviceMock).remove(any());

        final Vocabulary vocabulary = Generator.generateVocabulary();
        vocabulary.setUri(Generator.generateUri());
        final String fragment = IdentifierResolver.extractIdentifierFragment(vocabulary.getUri());
        mockMvc.perform(
                delete(PATH + "/" + fragment))
               .andExpect(status().is4xxClientError()).andReturn();
    }

    @Test
    void createVocabularyReturnsResponseWithLocationSpecifyingNamespaceWhenItIsDifferentFromConfiguredOne()
            throws Exception {
        final Vocabulary vocabulary = Generator.generateVocabulary();
        vocabulary.setUri(VOCABULARY_URI);
        final String configuredNamespace =
                "http://kbss.felk.cvut.cz/ontologies/termit/vocabularies/";
        configMock.getNamespace().setVocabulary(configuredNamespace);
        final MvcResult mvcResult = mockMvc.perform(
                post(PATH).content(toJson(vocabulary)).contentType(MediaType.APPLICATION_JSON_VALUE))
                                           .andExpect(status().isCreated()).andReturn();
        final String location = mvcResult.getResponse().getHeader(HttpHeaders.LOCATION);
        assertThat(location, containsString(QueryParams.NAMESPACE + "=" + NAMESPACE));
    }

    @Test
    void updateVocabularyUpdatesVocabularyUpdateToService() throws Exception {
        final Vocabulary vocabulary = generateVocabulary();
        vocabulary.setUri(VOCABULARY_URI);
        when(idResolverMock.resolveIdentifier(any(), any()))
                .thenReturn(VOCABULARY_URI);
        mockMvc.perform(put(PATH + "/test").contentType(MediaType.APPLICATION_JSON_VALUE)
                                           .content(toJson(vocabulary)))
               .andExpect(status().isNoContent());
        verify(serviceMock).update(vocabulary);
    }

    @Test
    void updateVocabularyThrowsValidationExceptionWhenVocabularyUriDiffersFromRequestBasedUri() throws Exception {
        final Vocabulary vocabulary = generateVocabulary();
        when(idResolverMock.resolveIdentifier(configMock.getNamespace().getVocabulary(), FRAGMENT))
                .thenReturn(VOCABULARY_URI);
        final MvcResult mvcResult = mockMvc
                .perform(put(PATH + "/" + FRAGMENT).contentType(MediaType.APPLICATION_JSON_VALUE)
                                                   .content(toJson(vocabulary)))
                .andExpect(status().isConflict()).andReturn();
        final ErrorInfo errorInfo = readValue(mvcResult, ErrorInfo.class);
        assertNotNull(errorInfo);
        assertThat(errorInfo.getMessage(),
                containsString("does not match the ID of the specified entity"));
        verify(serviceMock, never()).update(any());
    }

    @Test
    void updateVocabularyThrowsVocabularyImportExceptionWithMessageIdWhenServiceThrowsException()
            throws Exception {
        final Vocabulary vocabulary = generateVocabulary();
        vocabulary.setUri(VOCABULARY_URI);
        when(idResolverMock.resolveIdentifier(configMock.getNamespace().getVocabulary(), FRAGMENT))
                .thenReturn(VOCABULARY_URI);
        final String errorMsg = "Error message";
        final String errorMsgId = "message.id";
        when(serviceMock.update(any()))
                .thenThrow(new VocabularyImportException(errorMsg, errorMsgId));

        final MvcResult mvcResult = mockMvc
                .perform(put(PATH + "/" + FRAGMENT).contentType(MediaType.APPLICATION_JSON_VALUE)
                                                   .content(toJson(vocabulary)))
                .andExpect(status().isConflict()).andReturn();
        final ErrorInfo errorInfo = readValue(mvcResult, ErrorInfo.class);
        assertNotNull(errorInfo);
        assertEquals(errorMsg, errorInfo.getMessage());
        assertEquals(errorMsgId, errorInfo.getMessageId());
    }

    @Test
    void getTransitiveImportsReturnsCollectionOfImportIdentifiersRetrievedFromService()
            throws Exception {
        final Vocabulary vocabulary = generateVocabulary();
        vocabulary.setUri(VOCABULARY_URI);
        when(idResolverMock.resolveIdentifier(configMock.getNamespace().getVocabulary(), FRAGMENT))
                .thenReturn(VOCABULARY_URI);
        final Set<URI> imports = IntStream.range(0, 5).mapToObj(i -> Generator.generateUri())
                                          .collect(Collectors.toSet());
        when(serviceMock.getRequiredReference(VOCABULARY_URI)).thenReturn(vocabulary);
        when(serviceMock.getTransitivelyImportedVocabularies(vocabulary)).thenReturn(imports);

        final MvcResult mvcResult =
                mockMvc.perform(get(PATH + "/" + FRAGMENT + "/imports")).andExpect(status().isOk())
                       .andReturn();
        final Set<URI> result = readValue(mvcResult, new TypeReference<Set<URI>>() {
        });
        assertEquals(imports, result);
        verify(serviceMock).getRequiredReference(VOCABULARY_URI);
        verify(serviceMock).getTransitivelyImportedVocabularies(vocabulary);
    }

    @Test
    void getTransitiveImportsReturnsEmptyCollectionWhenNoImportsAreFoundForVocabulary()
            throws Exception {
        final Vocabulary vocabulary = generateVocabulary();
        vocabulary.setUri(VOCABULARY_URI);
        when(idResolverMock.resolveIdentifier(configMock.getNamespace().getVocabulary(), FRAGMENT))
                .thenReturn(VOCABULARY_URI);
        when(serviceMock.getRequiredReference(VOCABULARY_URI)).thenReturn(vocabulary);
        when(serviceMock.getTransitivelyImportedVocabularies(vocabulary))
                .thenReturn(Collections.emptySet());

        final MvcResult mvcResult =
                mockMvc.perform(get(PATH + "/" + FRAGMENT + "/imports")).andExpect(status().isOk())
                       .andReturn();
        final Set<URI> result = readValue(mvcResult, new TypeReference<Set<URI>>() {
        });
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(serviceMock).getRequiredReference(VOCABULARY_URI);
        verify(serviceMock).getTransitivelyImportedVocabularies(vocabulary);
    }

    @Test
    void getHistoryReturnsListOfChangeRecordsForSpecifiedVocabulary() throws Exception {
        final Vocabulary vocabulary = generateVocabulary();
        vocabulary.setUri(VOCABULARY_URI);
        when(idResolverMock.resolveIdentifier(configMock.getNamespace().getVocabulary(), FRAGMENT))
                .thenReturn(VOCABULARY_URI);
        when(serviceMock.getRequiredReference(VOCABULARY_URI)).thenReturn(vocabulary);
        final List<AbstractChangeRecord> records =
                Generator.generateChangeRecords(vocabulary, user);
        when(serviceMock.getChanges(vocabulary)).thenReturn(records);

        final MvcResult mvcResult =
                mockMvc.perform(get(PATH + "/" + FRAGMENT + "/history")).andExpect(status().isOk())
                       .andReturn();
        final List<AbstractChangeRecord> result =
                readValue(mvcResult, new TypeReference<List<AbstractChangeRecord>>() {
                });
        assertNotNull(result);
        assertEquals(records, result);
        verify(serviceMock).getChanges(vocabulary);
    }

    @Test
    void getHistoryOfContentReturnsListOfChangeRecordsForTermsInSpecifiedVocabulary()
            throws Exception {
        final Vocabulary vocabulary = generateVocabulary();
        vocabulary.setUri(VOCABULARY_URI);
        when(idResolverMock.resolveIdentifier(configMock.getNamespace().getVocabulary(), FRAGMENT))
                .thenReturn(VOCABULARY_URI);
        when(serviceMock.getRequiredReference(VOCABULARY_URI)).thenReturn(vocabulary);
        final Term term = generateTerm();
        final List<AbstractChangeRecord> records = Generator.generateChangeRecords(term, user);
        when(serviceMock.getChangesOfContent(vocabulary)).thenReturn(records);
        final MvcResult mvcResult =
                mockMvc.perform(get(PATH + "/" + FRAGMENT + "/history-of-content"))
                       .andExpect(status().isOk())
                       .andReturn();
        final List<AbstractChangeRecord> result =
                readValue(mvcResult, new TypeReference<List<AbstractChangeRecord>>() {
                });
        assertNotNull(result);
        assertEquals(records, result);
        verify(serviceMock).getChangesOfContent(vocabulary);
    }

    @Test
    void validateExecutesServiceValidate() throws Exception {
        final Vocabulary vocabulary = generateVocabulary();
        vocabulary.setUri(VOCABULARY_URI);
        when(idResolverMock.resolveIdentifier(configMock.getNamespace().getVocabulary(), FRAGMENT))
                .thenReturn(VOCABULARY_URI);
        when(serviceMock.getRequiredReference(VOCABULARY_URI)).thenReturn(vocabulary);
        final List<ValidationResult> records = Generator.generateValidationRecords();
        when(serviceMock.validateContents(vocabulary)).thenReturn(records);


        final MvcResult mvcResult = mockMvc.perform(get(PATH + "/" + FRAGMENT + "/validate"))
                                           .andExpect(status().isOk())
                                           .andReturn();
        final List<ValidationResult> result =
                readValue(mvcResult, new TypeReference<List<ValidationResult>>() {
                });
        assertNotNull(result);
        assertEquals(records.stream().map(ValidationResult::getId).collect(Collectors.toList()),
                result.stream().map(ValidationResult::getId).collect(Collectors.toList()));
        verify(serviceMock).validateContents(vocabulary);
    }
}
