package cz.cvut.kbss.termit.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import cz.cvut.kbss.termit.dto.AggregatedChangeInfo;
import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.dto.listing.VocabularyDto;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.AssetRemovalException;
import cz.cvut.kbss.termit.exception.importing.VocabularyImportException;
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
import org.topbraid.shacl.vocabulary.SH;

import java.math.BigInteger;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static cz.cvut.kbss.termit.environment.util.ContainsSameEntities.containsSameEntities;
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
        final List<VocabularyDto> vocabularies = IntStream.range(0, 5).mapToObj(
                                                                  i -> Environment.getDtoMapper().vocabularyToVocabularyDto(generateVocabulary()))
                                                          .collect(Collectors.toList());
        when(serviceMock.findAll()).thenReturn(vocabularies);

        final MvcResult mvcResult =
                mockMvc.perform(get(PATH)).andExpect(status().isOk()).andReturn();
        final List<VocabularyDto> result = readValue(mvcResult, new TypeReference<List<VocabularyDto>>() {
        });
        assertThat(result, containsSameEntities(vocabularies));
    }

    private Vocabulary generateVocabulary() {
        return Generator.generateVocabularyWithId();
    }

    @Test
    void getAllReturnsLastModifiedHeader() throws Exception {
        final List<VocabularyDto> vocabularies = Collections.singletonList(
                Environment.getDtoMapper().vocabularyToVocabularyDto(generateVocabulary()));
        when(serviceMock.findAll()).thenReturn(vocabularies);
        // Round to seconds
        final long lastModified = (System.currentTimeMillis() / 1000) * 1000;
        when(serviceMock.getLastModified()).thenReturn(lastModified);

        final MvcResult mvcResult = mockMvc.perform(get(PATH)).andExpect(status().isOk()).andReturn();
        final String lastModifiedHeader = mvcResult.getResponse().getHeader(HttpHeaders.LAST_MODIFIED);
        assertNotNull(lastModifiedHeader);
        ZonedDateTime zdt = ZonedDateTime.parse(lastModifiedHeader, DateTimeFormatter.RFC_1123_DATE_TIME);
        assertEquals(lastModified, zdt.toInstant().toEpochMilli());
    }

    @Test
    void getAllReturnsNotModifiedWhenLastModifiedDateIsBeforeIfModifiedSinceHeaderValue() throws Exception {
        // Round to seconds
        final long lastModified = (System.currentTimeMillis() - 60 * 1000);
        when(serviceMock.getLastModified()).thenReturn(lastModified);

        mockMvc.perform(get(PATH).header(HttpHeaders.IF_MODIFIED_SINCE,
                                         DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now())))
               .andExpect(status().isNotModified());
        verify(serviceMock).getLastModified();
        verify(serviceMock, never()).findAll();
    }

    @Test
    void createVocabularyPersistsSpecifiedVocabulary() throws Exception {
        final Vocabulary vocabulary = Generator.generateVocabulary();
        vocabulary.setUri(Generator.generateUri());

        mockMvc.perform(post(PATH).content(toJson(vocabulary)).contentType(MediaType.APPLICATION_JSON_VALUE))
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

        final MvcResult mvcResult = mockMvc.perform(post(PATH).content(toJson(vocabulary))
                                                              .contentType(MediaType.APPLICATION_JSON_VALUE))
                                           .andExpect(status().isCreated()).andReturn();
        verifyLocationEquals(PATH + "/" + fragment, mvcResult);
    }

    @Test
    void createVocabularyRunsImportWhenFileIsUploaded() throws Exception {
        final Vocabulary vocabulary = Generator.generateVocabulary();
        vocabulary.setUri(URI.create(NAMESPACE + FRAGMENT));
        when(serviceMock.importVocabulary(anyBoolean(), any())).thenReturn(vocabulary);
        final MockMultipartFile upload = new MockMultipartFile("file", "test-glossary.ttl",
                                                               Constants.MediaType.TURTLE,
                                                               Environment.loadFile("data/test-glossary.ttl"));
        final MvcResult mvcResult = mockMvc.perform(multipart(PATH + "/import").file(upload)
                                                                               .param("rename", "false"))
                                           .andExpect(status().isCreated())
                                           .andReturn();
        verifyLocationEquals(PATH + "/" + FRAGMENT, mvcResult);
        assertThat(mvcResult.getResponse().getHeader(HttpHeaders.LOCATION),
                   containsString(QueryParams.NAMESPACE + "=" + NAMESPACE));
        verify(serviceMock).importVocabulary(false, upload);
    }

    @Test
    void reImportVocabularyRunsImportForUploadedFile() throws Exception {
        when(configMock.getNamespace().getVocabulary()).thenReturn(NAMESPACE);
        final Vocabulary vocabulary = Generator.generateVocabulary();
        vocabulary.setUri(URI.create(NAMESPACE + FRAGMENT));
        when(idResolverMock.resolveIdentifier(NAMESPACE, FRAGMENT)).thenReturn(vocabulary.getUri());
        when(serviceMock.importVocabulary(any(URI.class), any())).thenReturn(vocabulary);
        final MockMultipartFile upload = new MockMultipartFile("file", "test-glossary.ttl",
                                                               Constants.MediaType.TURTLE,
                                                               Environment.loadFile("data/test-glossary.ttl"));
        final MvcResult mvcResult = mockMvc.perform(multipart(PATH + "/" + FRAGMENT + "/import").file(upload))
                                           .andExpect(status().isCreated())
                                           .andReturn();
        verifyLocationEquals(PATH + "/" + FRAGMENT, mvcResult);
        assertThat(mvcResult.getResponse().getHeader(HttpHeaders.LOCATION),
                   containsString(QueryParams.NAMESPACE + "=" + NAMESPACE));
        verify(serviceMock).importVocabulary(vocabulary.getUri(), upload);
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
        mockMvc.perform(delete(PATH + "/" + fragment)).andExpect(status().is2xxSuccessful()).andReturn();
    }

    @Test
    void removeVocabularyReturns4xxForNotRemovableVocabulary() throws Exception {
        Mockito.doThrow(new AssetRemovalException("Vocabulary cannot be removed. It contains terms."))
               .when(serviceMock).remove(any());

        final Vocabulary vocabulary = Generator.generateVocabulary();
        vocabulary.setUri(Generator.generateUri());
        final String fragment = IdentifierResolver.extractIdentifierFragment(vocabulary.getUri());
        mockMvc.perform(delete(PATH + "/" + fragment)).andExpect(status().is4xxClientError()).andReturn();
    }

    @Test
    void createVocabularyReturnsAlwaysResponseWithLocationSpecifyingNamespace()
            throws Exception {
        final Vocabulary vocabulary = Generator.generateVocabulary();
        vocabulary.setUri(VOCABULARY_URI);
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
        final Vocabulary vocabulary = generateVocabularyAndInitReferenceResolution();
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
    void runTextAnalysisOnAllTermsInvokesTextAnalysisOnAllTermsFromService() throws Exception {
        final Vocabulary vocabulary = generateVocabulary();
        vocabulary.setUri(VOCABULARY_URI);
        when(sut.getById(FRAGMENT, Optional.of(NAMESPACE))).thenReturn(vocabulary);
        mockMvc.perform(put(PATH + "/" + FRAGMENT + "/terms/text-analysis")).andExpect(status().isAccepted());
        verify(serviceMock).runTextAnalysisOnAllTerms(vocabulary);
    }

    @Test
    void runTextAnalysisOnAllVocabulariesInvokesTextAnalysisOnAllVocabulariesFromService() throws Exception {
        mockMvc.perform(get(PATH + "/text-analysis")).andExpect(status().isAccepted());
        verify(serviceMock).runTextAnalysisOnAllVocabularies();
    }

    @Test
    void getHistoryReturnsListOfChangeRecordsForSpecifiedVocabulary() throws Exception {
        final Vocabulary vocabulary = generateVocabularyAndInitReferenceResolution();
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
    void getHistoryOfContentReturnsListOfAggregatedChangeObjectsForTermsInSpecifiedVocabulary()
            throws Exception {
        final Vocabulary vocabulary = generateVocabularyAndInitReferenceResolution();
        final List<AggregatedChangeInfo> changes = IntStream.range(0, 10).mapToObj(i -> {
            final AggregatedChangeInfo ch = new AggregatedChangeInfo(LocalDate.now().minusDays(i).toString(),
                                                                     new BigInteger(Integer.toString(
                                                                             Generator.randomInt(1, 10))));
            ch.addType(i % 2 == 0 ? cz.cvut.kbss.termit.util.Vocabulary.s_c_vytvoreni_entity :
                       cz.cvut.kbss.termit.util.Vocabulary.s_c_uprava_entity);
            return ch;
        }).collect(Collectors.toList());
        when(serviceMock.getChangesOfContent(vocabulary)).thenReturn(changes);
        final MvcResult mvcResult =
                mockMvc.perform(get(PATH + "/" + FRAGMENT + "/history-of-content"))
                       .andExpect(status().isOk())
                       .andReturn();
        final List<AggregatedChangeInfo> result =
                readValue(mvcResult, new TypeReference<List<AggregatedChangeInfo>>() {
                });
        assertNotNull(result);
        assertEquals(changes, result);
        verify(serviceMock).getChangesOfContent(vocabulary);
    }

    @Test
    void validateExecutesServiceValidate() throws Exception {
        final Vocabulary vocabulary = generateVocabularyAndInitReferenceResolution();
        final List<ValidationResult> records = Collections.singletonList(new ValidationResult()
                                                                                 .setTermUri(Generator.generateUri())
                                                                                 .setIssueCauseUri(
                                                                                         Generator.generateUri())
                                                                                 .setSeverity(URI.create(
                                                                                         SH.Violation.toString())));
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

    private Vocabulary generateVocabularyAndInitReferenceResolution() {
        final Vocabulary vocabulary = generateVocabulary();
        vocabulary.setUri(VOCABULARY_URI);
        when(idResolverMock.resolveIdentifier(configMock.getNamespace().getVocabulary(), FRAGMENT))
                .thenReturn(VOCABULARY_URI);
        when(serviceMock.getRequiredReference(VOCABULARY_URI)).thenReturn(vocabulary);
        return vocabulary;
    }

    @Test
    void createSnapshotCreatesSnapshotOfVocabularyWithSpecifiedIdentification() throws Exception {
        final Vocabulary vocabulary = generateVocabularyAndInitReferenceResolution();
        final Snapshot snapshot = Generator.generateSnapshot(vocabulary);
        when(serviceMock.createSnapshot(any())).thenReturn(snapshot);
        mockMvc.perform(post(PATH + "/" + FRAGMENT + "/versions"))
               .andExpect(status().isCreated());
        verify(serviceMock).createSnapshot(vocabulary);
    }

    @Test
    void createSnapshotReturnsLocationHeaderWithSnapshotApiPath() throws Exception {
        final Vocabulary vocabulary = generateVocabularyAndInitReferenceResolution();
        final Snapshot snapshot = Generator.generateSnapshot(vocabulary);
        when(serviceMock.createSnapshot(any())).thenReturn(snapshot);
        final MvcResult mvcResult = mockMvc.perform(post(PATH + "/" + FRAGMENT + "/versions"))
                                           .andExpect(status().isCreated())
                                           .andReturn();
        verifyLocationEquals(PATH + "/" + IdentifierResolver.extractIdentifierFragment(snapshot.getUri()), mvcResult);
    }

    @Test
    void getSnapshotsReturnsListOfVocabularySnapshotsWhenFilterInstantIsNotProvided() throws Exception {
        final Vocabulary vocabulary = generateVocabularyAndInitReferenceResolution();
        final List<Snapshot> snapshots = IntStream.range(0, 5).mapToObj(i -> {
            final Snapshot snapshot = Generator.generateSnapshot(vocabulary);
            snapshot.setUri(Generator.generateUri());
            snapshot.setCreated(Instant.now().truncatedTo(ChronoUnit.SECONDS).minus(i, ChronoUnit.DAYS));
            return snapshot;
        }).collect(Collectors.toList());
        when(serviceMock.findSnapshots(vocabulary)).thenReturn(snapshots);

        final MvcResult mvcResult = mockMvc.perform(
                                                   get(PATH + "/" + FRAGMENT + "/versions").accept(MediaType.APPLICATION_JSON_VALUE))
                                           .andExpect(status().isOk())
                                           .andReturn();
        final List<Snapshot> result = readValue(mvcResult, new TypeReference<List<Snapshot>>() {
        });
        assertThat(result, containsSameEntities(snapshots));
        verify(serviceMock).findSnapshots(vocabulary);
        verify(serviceMock, never()).findVersionValidAt(any(), any());
    }

    @Test
    void getSnapshotsReturnsVocabularySnapshotValidAtSpecifiedInstant() throws Exception {
        final Vocabulary vocabulary = generateVocabularyAndInitReferenceResolution();
        final Vocabulary snapshot = new Vocabulary();
        final Instant instant = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        snapshot.setUri(URI.create(vocabulary.getUri().toString() + "/version/" + instant));
        snapshot.setLabel(FRAGMENT + " - Snapshot");
        when(serviceMock.findVersionValidAt(eq(vocabulary), any(Instant.class))).thenReturn(snapshot);

        final MvcResult mvcResult = mockMvc.perform(
                                                   get(PATH + "/" + FRAGMENT + "/versions")
                                                           .param("at", instant.toString())
                                                           .accept(MediaType.APPLICATION_JSON_VALUE))
                                           .andExpect(status().isOk())
                                           .andReturn();
        final Vocabulary result = readValue(mvcResult, Vocabulary.class);
        assertEquals(snapshot, result);
        verify(serviceMock).findVersionValidAt(vocabulary, instant);
        verify(serviceMock, never()).findSnapshots(any());
    }

    @Test
    void getSnapshotsThrowsBadRequestWhenAtIsNotValidInstantString() throws Exception {
        generateVocabularyAndInitReferenceResolution();
        final Instant instant = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        mockMvc.perform(get(PATH + "/" + FRAGMENT + "/versions").param("at", Date.from(instant).toString()))
               .andExpect(status().isBadRequest());
        verify(serviceMock, never()).findVersionValidAt(any(), any());
        verify(serviceMock, never()).findSnapshots(any());
    }
}
