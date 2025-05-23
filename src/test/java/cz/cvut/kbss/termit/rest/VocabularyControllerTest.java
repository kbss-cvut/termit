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
package cz.cvut.kbss.termit.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.termit.dto.AggregatedChangeInfo;
import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.dto.acl.AccessControlListDto;
import cz.cvut.kbss.termit.dto.filter.ChangeRecordFilterDto;
import cz.cvut.kbss.termit.dto.listing.VocabularyDto;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.AssetRemovalException;
import cz.cvut.kbss.termit.exception.importing.VocabularyImportException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.acl.AccessControlList;
import cz.cvut.kbss.termit.model.acl.AccessControlRecord;
import cz.cvut.kbss.termit.model.acl.AccessLevel;
import cz.cvut.kbss.termit.model.acl.UserAccessControlRecord;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.rest.handler.ErrorInfo;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.VocabularyService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.Constants.QueryParams;
import cz.cvut.kbss.termit.util.TypeAwareFileSystemResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.io.File;
import java.math.BigInteger;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static cz.cvut.kbss.termit.environment.util.ContainsSameEntities.containsSameEntities;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
        final List<VocabularyDto> result = readValue(mvcResult, new TypeReference<>() {
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
    void removeVocabularyCallsRemove() throws Exception {
        final Vocabulary vocabulary = Generator.generateVocabulary();
        vocabulary.setUri(VOCABULARY_URI);

        when(idResolverMock.resolveIdentifier(configMock.getNamespace().getVocabulary(), FRAGMENT))
                .thenReturn(VOCABULARY_URI);
        when(serviceMock.findRequired(VOCABULARY_URI)).thenReturn(vocabulary);

        mockMvc.perform(delete(PATH + "/" + FRAGMENT)).andExpect(status().is2xxSuccessful()).andReturn();

        final ArgumentCaptor<Vocabulary> captor = ArgumentCaptor.forClass(Vocabulary.class);
        verify(serviceMock).remove(captor.capture());

        assertEquals(vocabulary, captor.getValue());
        // ensures that the object was really a Vocabulary, and not a dto
        assertEquals(Vocabulary.class, captor.getValue().getClass());
    }

    @Test
    void removeVocabularyReturns2xxForEmptyVocabulary() throws Exception {
        final Vocabulary vocabulary = Generator.generateVocabulary();
        vocabulary.setUri(VOCABULARY_URI);
        when(idResolverMock.resolveIdentifier(configMock.getNamespace().getVocabulary(), FRAGMENT))
                .thenReturn(VOCABULARY_URI);
        when(serviceMock.findRequired(VOCABULARY_URI)).thenReturn(vocabulary);
        mockMvc.perform(delete(PATH + "/" + FRAGMENT)).andExpect(status().is2xxSuccessful()).andReturn();
    }

    @Test
    void removeVocabularyReturns4xxForNotRemovableVocabulary() throws Exception {
        Mockito.doThrow(new AssetRemovalException("Vocabulary cannot be removed. It contains terms."))
               .when(serviceMock).remove(any());

        final Vocabulary vocabulary = Generator.generateVocabulary();
        vocabulary.setUri(VOCABULARY_URI);
        when(idResolverMock.resolveIdentifier(configMock.getNamespace().getVocabulary(), FRAGMENT))
                .thenReturn(VOCABULARY_URI);
        when(serviceMock.findRequired(vocabulary.getUri())).thenReturn(vocabulary);
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
        when(serviceMock.getReference(VOCABULARY_URI)).thenReturn(vocabulary);
        when(serviceMock.getTransitivelyImportedVocabularies(vocabulary)).thenReturn(imports);

        final MvcResult mvcResult =
                mockMvc.perform(get(PATH + "/" + FRAGMENT + "/imports")).andExpect(status().isOk())
                       .andReturn();
        final Set<URI> result = readValue(mvcResult, new TypeReference<>() {
        });
        assertEquals(imports, result);
        verify(serviceMock).getReference(VOCABULARY_URI);
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
        final Set<URI> result = readValue(mvcResult, new TypeReference<>() {
        });
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(serviceMock).getReference(VOCABULARY_URI);
        verify(serviceMock).getTransitivelyImportedVocabularies(vocabulary);
    }

    @Test
    void runTextAnalysisOnAllTermsInvokesTextAnalysisOnAllTermsFromService() throws Exception {
        final Vocabulary vocabulary = generateVocabulary();
        vocabulary.setUri(VOCABULARY_URI);
        when(idResolverMock.resolveIdentifier(null, FRAGMENT)).thenReturn(vocabulary.getUri());
        mockMvc.perform(put(PATH + "/" + FRAGMENT + "/terms/text-analysis")).andExpect(status().isAccepted());
        verify(serviceMock).runTextAnalysisOnAllTerms(vocabulary.getUri());
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
        final ChangeRecordFilterDto emptyFilter = new ChangeRecordFilterDto();
        when(serviceMock.getChanges(vocabulary, emptyFilter)).thenReturn(records);

        final MvcResult mvcResult =
                mockMvc.perform(get(PATH + "/" + FRAGMENT + "/history")).andExpect(status().isOk())
                       .andReturn();
        final List<AbstractChangeRecord> result =
                readValue(mvcResult, new TypeReference<>() {
                });
        assertNotNull(result);
        assertEquals(records, result);
        verify(serviceMock).getChanges(vocabulary, emptyFilter);
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
                readValue(mvcResult, new TypeReference<>() {
                });
        assertNotNull(result);
        assertEquals(changes, result);
        verify(serviceMock).getChangesOfContent(vocabulary);
    }

    private Vocabulary generateVocabularyAndInitReferenceResolution() {
        final Vocabulary vocabulary = generateVocabulary();
        vocabulary.setUri(VOCABULARY_URI);
        when(idResolverMock.resolveIdentifier(configMock.getNamespace().getVocabulary(), FRAGMENT))
                .thenReturn(VOCABULARY_URI);
        when(serviceMock.getReference(VOCABULARY_URI)).thenReturn(vocabulary);
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
        final List<Snapshot> result = readValue(mvcResult, new TypeReference<>() {
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
        snapshot.setLabel(MultilingualString.create(FRAGMENT + " - Snapshot", Environment.LANGUAGE));
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

    @Test
    void getAccessControlListReturnsAccessControlListRetrievedFromService() throws Exception {
        final Vocabulary vocabulary = generateVocabularyAndInitReferenceResolution();
        final AccessControlList acl = Generator.generateAccessControlList(true);
        final AccessControlListDto dto = Environment.getDtoMapper().accessControlListToDto(acl);
        when(serviceMock.getAccessControlList(vocabulary)).thenReturn(dto);

        final MvcResult mvcResult = mockMvc.perform(get(PATH + "/" + FRAGMENT + "/acl")).andReturn();
        final AccessControlListDto result = readValue(mvcResult, AccessControlListDto.class);
        assertEquals(acl.getUri(), result.getUri());
        assertThat(result.getRecords(), containsSameEntities(acl.getRecords()));
    }

    @Test
    void addAccessControlRecordsAddsRecordsToVocabularyViaService() throws Exception {
        final Vocabulary vocabulary = generateVocabularyAndInitReferenceResolution();
        final AccessControlRecord<?> toAdd = Generator.generateAccessControlRecords().get(0);
        toAdd.setUri(null);

        // Explicitly use type reference to force Jackson to serialize the records with type info
        mockMvc.perform(post(PATH + "/" + FRAGMENT + "/acl/records").content(toJson(toAdd))
                                                                    .contentType(MediaType.APPLICATION_JSON))
               .andExpect(status().isNoContent());
        final ArgumentCaptor<AccessControlRecord<?>> captor = ArgumentCaptor.forClass(AccessControlRecord.class);
        verify(serviceMock).addAccessControlRecords(eq(vocabulary), captor.capture());
        assertEquals(toAdd.getHolder().getUri(), captor.getValue().getHolder().getUri());
        assertEquals(toAdd.getAccessLevel(), captor.getValue().getAccessLevel());
    }

    @Test
    void removeAccessControlRecordRemovesRecordFromVocabularyViaService() throws Exception {
        final Vocabulary vocabulary = generateVocabularyAndInitReferenceResolution();
        final AccessControlRecord<?> toRemove = Generator.generateAccessControlRecords().get(0);

        // Explicitly use type reference to force Jackson to serialize the records with type info
        mockMvc.perform(delete(PATH + "/" + FRAGMENT + "/acl/records").content(toJson(toRemove))
                                                                      .contentType(MediaType.APPLICATION_JSON))
               .andExpect(status().isNoContent());
        final ArgumentCaptor<AccessControlRecord<?>> captor = ArgumentCaptor.forClass(AccessControlRecord.class);
        verify(serviceMock).removeAccessControlRecord(eq(vocabulary), captor.capture());
        assertEquals(toRemove.getUri(), captor.getValue().getUri());
    }

    @Test
    void updateAccessControlLevelUpdatesAccessLevelOfSpecifiedRecordViaService() throws Exception {
        final Vocabulary vocabulary = generateVocabularyAndInitReferenceResolution();
        final UserAccessControlRecord record = new UserAccessControlRecord();
        record.setUri(Generator.generateUri());
        record.setAccessLevel(AccessLevel.SECURITY);
        record.setHolder(Generator.generateUserWithId());

        mockMvc.perform(put(PATH + "/" + FRAGMENT + "/acl/records/" + IdentifierResolver.extractIdentifierFragment(
                       record.getUri())).content(toJson(record)).contentType(MediaType.APPLICATION_JSON))
               .andExpect(status().isNoContent());
        final ArgumentCaptor<AccessControlRecord<?>> captor = ArgumentCaptor.forClass(AccessControlRecord.class);
        verify(serviceMock).updateAccessControlLevel(eq(vocabulary), captor.capture());
        assertEquals(record, captor.getValue());
    }

    @Test
    void updateAccessControlLevelThrowsBadRequestForRecordUriNotMatchingPath() throws Exception {
        final UserAccessControlRecord record = new UserAccessControlRecord();
        record.setUri(Generator.generateUri());
        record.setAccessLevel(AccessLevel.SECURITY);
        record.setHolder(Generator.generateUserWithId());

        mockMvc.perform(put(PATH + "/" + FRAGMENT + "/acl/records/" + Generator.randomInt())
                                .content(toJson(record)).contentType(MediaType.APPLICATION_JSON))
               .andExpect(status().isBadRequest());
        verify(serviceMock, never()).updateAccessControlLevel(any(Vocabulary.class), any(AccessControlRecord.class));
    }

    @Test
    void getAccessLevelRetrievesAccessLevelToSpecifiedVocabulary() throws Exception {
        final Vocabulary vocabulary = generateVocabularyAndInitReferenceResolution();
        when(serviceMock.getAccessLevel(vocabulary)).thenReturn(AccessLevel.SECURITY);

        final MvcResult mvcResult = mockMvc.perform(get(PATH + "/" + FRAGMENT + "/access-level")).andReturn();
        final AccessLevel result = readValue(mvcResult, AccessLevel.class);
        assertEquals(AccessLevel.SECURITY, result);
        verify(serviceMock).getAccessLevel(vocabulary);
    }

    @Test
    void getExcelTemplateFileReturnsExcelTemplateFileRetrievedFromServiceAsAttachment() throws Exception {
        when(serviceMock.getExcelImportTemplateFile()).thenReturn(new TypeAwareFileSystemResource(
                new File(getClass().getClassLoader().getResource("template/termit-import.xlsx").toURI()),
                Constants.MediaType.EXCEL));

        final MvcResult mvcResult = mockMvc.perform(get(PATH + "/import/template")).andReturn();
        assertThat(mvcResult.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION), containsString("attachment"));
        assertThat(mvcResult.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION),
                   containsString("filename=\"termit-import.xlsx\""));
        verify(serviceMock).getExcelImportTemplateFile();
    }

    @Test
    void getExcelTemplateFileReturnsExcelTermTranslationsTemplateFileRetrievedFromServiceAsAttachment()
            throws Exception {
        when(serviceMock.getExcelTranslationsImportTemplateFile()).thenReturn(new TypeAwareFileSystemResource(
                new File(getClass().getClassLoader().getResource("template/termit-translations-import.xlsx").toURI()),
                Constants.MediaType.EXCEL));

        final MvcResult mvcResult = mockMvc.perform(
                get(PATH + "/import/template").queryParam("translationsOnly", Boolean.toString(true))).andReturn();
        assertThat(mvcResult.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION), containsString("attachment"));
        assertThat(mvcResult.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION),
                   containsString("filename=\"termit-translations-import.xlsx\""));
        verify(serviceMock).getExcelTranslationsImportTemplateFile();
    }

    @Test
    void getDetailedHistoryOfContentReturnsListOfChangeRecordsWhenNoFilterIsSpecified() throws Exception {
        final int pageSize = Integer.parseInt(VocabularyController.DEFAULT_PAGE_SIZE);
        final Vocabulary vocabulary = generateVocabularyAndInitReferenceResolution();
        final Term term = Generator.generateTermWithId();
        final List<AbstractChangeRecord> changeRecords = IntStream.range(0, 5).mapToObj(
                i -> Generator.generateChangeRecords(term, user)).flatMap(List::stream).toList();
        final ChangeRecordFilterDto filter = new ChangeRecordFilterDto();
        final Pageable pageable = Pageable.ofSize(pageSize);

        doReturn(changeRecords).when(serviceMock).getDetailedHistoryOfContent(vocabulary, filter, pageable);

        final MvcResult mvcResult = mockMvc.perform(get(PATH + "/" + FRAGMENT + "/history-of-content/detail"))
                                           .andExpect(status().isOk()).andReturn();
        final List<AbstractChangeRecord> result =
                readValue(mvcResult, new TypeReference<>() {
                });
        assertNotNull(result);
        assertEquals(changeRecords, result);
        verify(serviceMock).getDetailedHistoryOfContent(vocabulary, filter, pageable);
    }

    @Test
    void getLanguagesRetrievesAndReturnsListOfLanguagesUsedInVocabulary() throws Exception {
        when(idResolverMock.resolveIdentifier(NAMESPACE, FRAGMENT)).thenReturn(VOCABULARY_URI);
        final List<String> languages = List.of(Environment.LANGUAGE, "cs", "de");
        when(serviceMock.getLanguages(VOCABULARY_URI)).thenReturn(languages);

        final MvcResult mvcResult = mockMvc.perform(
                get(PATH + "/" + FRAGMENT + "/languages").queryParam(QueryParams.NAMESPACE, NAMESPACE)).andReturn();
        final List<String> result = readValue(mvcResult, new TypeReference<>() {
        });
        assertEquals(languages, result);
        verify(serviceMock).getLanguages(VOCABULARY_URI);
    }

    @Test
    void reImportVocabularyRunsTermTranslationsImportForUploadedFileWhenTranslationsOnlyIsSpecified() throws Exception {
        when(configMock.getNamespace().getVocabulary()).thenReturn(NAMESPACE);
        final Vocabulary vocabulary = Generator.generateVocabulary();
        vocabulary.setUri(URI.create(NAMESPACE + FRAGMENT));
        when(idResolverMock.resolveIdentifier(NAMESPACE, FRAGMENT)).thenReturn(vocabulary.getUri());
        when(serviceMock.importTermTranslations(any(URI.class), any())).thenReturn(vocabulary);
        final MockMultipartFile upload = new MockMultipartFile("file", "vocabulary.xlsx",
                                                               Constants.MediaType.EXCEL,
                                                               Environment.loadFile("data/import-simple-en-cs.xlsx"));
        final MvcResult mvcResult = mockMvc.perform(multipart(PATH + "/" + FRAGMENT + "/import").file(upload)
                                                                                                .queryParam(
                                                                                                        "translationsOnly",
                                                                                                        "true"))
                                           .andExpect(status().isCreated())
                                           .andReturn();
        verifyLocationEquals(PATH + "/" + FRAGMENT, mvcResult);
        assertThat(mvcResult.getResponse().getHeader(HttpHeaders.LOCATION),
                   containsString(QueryParams.NAMESPACE + "=" + NAMESPACE));
        verify(serviceMock).importTermTranslations(vocabulary.getUri(), upload);
    }
}
