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
package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.dto.acl.AccessControlListDto;
import cz.cvut.kbss.termit.dto.listing.TermDto;
import cz.cvut.kbss.termit.dto.listing.VocabularyDto;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.event.VocabularyContentModifiedEvent;
import cz.cvut.kbss.termit.event.VocabularyCreatedEvent;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.acl.AccessControlList;
import cz.cvut.kbss.termit.model.acl.AccessLevel;
import cz.cvut.kbss.termit.model.acl.UserAccessControlRecord;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.persistence.context.VocabularyContextMapper;
import cz.cvut.kbss.termit.persistence.snapshot.SnapshotCreator;
import cz.cvut.kbss.termit.service.export.ExportFormat;
import cz.cvut.kbss.termit.service.repository.ChangeRecordService;
import cz.cvut.kbss.termit.service.repository.VocabularyRepositoryService;
import cz.cvut.kbss.termit.service.security.authorization.VocabularyAuthorizationService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.TypeAwareResource;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static cz.cvut.kbss.termit.environment.Environment.termsToDtos;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VocabularyServiceTest {

    @Mock
    TermService termService;

    @Mock
    private VocabularyRepositoryService repositoryService;

    @Mock
    private ChangeRecordService changeRecordService;

    @Mock
    private VocabularyContextMapper contextMapper;

    @Mock
    private AccessControlListService aclService;

    @Mock
    private VocabularyAuthorizationService authorizationService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ApplicationContext appContext;

    @Spy
    @InjectMocks
    private VocabularyService sut;

    @BeforeEach
    void setUp() {
        sut.setApplicationEventPublisher(eventPublisher);
    }

    @Test
    void runTextAnalysisOnAllTermsInvokesTextAnalysisOnAllTermsInVocabulary() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Term termOne = Generator.generateTermWithId(vocabulary.getUri());
        final Term termTwo = Generator.generateTermWithId(vocabulary.getUri());
        List<TermDto> terms = termsToDtos(Arrays.asList(termOne, termTwo));
        when(termService.findAll(vocabulary)).thenReturn(terms);
        when(contextMapper.getVocabularyContext(vocabulary.getUri())).thenReturn(vocabulary.getUri());
        when(repositoryService.getTransitivelyImportedVocabularies(vocabulary)).thenReturn(Collections.emptyList());
        when(repositoryService.findRequired(vocabulary.getUri())).thenReturn(vocabulary);
        sut.runTextAnalysisOnAllTerms(vocabulary);
        verify(termService).analyzeTermDefinition(termOne, vocabulary.getUri());
        verify(termService).analyzeTermDefinition(termTwo, vocabulary.getUri());
    }

    @Test
    void runTextAnalysisOnAllTermsInvokesTextAnalysisOnAllVocabularies() {
        final Vocabulary v = Generator.generateVocabularyWithId();
        final List<VocabularyDto> vocabularies = Collections.singletonList(Environment.getDtoMapper()
                                                                                      .vocabularyToVocabularyDto(v));
        final Term term = Generator.generateTermWithId(v.getUri());
        when(repositoryService.findAll()).thenReturn(vocabularies);
        when(contextMapper.getVocabularyContext(v.getUri())).thenReturn(v.getUri());
        when(termService.findAll(v)).thenReturn(Collections.singletonList(new TermDto(term)));
        sut.runTextAnalysisOnAllVocabularies();

        verify(termService).analyzeTermDefinition(term, v.getUri());
    }

    @Test
    void createSnapshotCreatesSnapshotOfSpecifiedVocabulary() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final SnapshotCreator snapshotCreator = mock(SnapshotCreator.class);
        when(appContext.getBean(SnapshotCreator.class)).thenReturn(snapshotCreator);
        final Snapshot snapshot = Generator.generateSnapshot(vocabulary);
        when(snapshotCreator.createSnapshot(any(Vocabulary.class))).thenReturn(snapshot);
        final Vocabulary snapshotVoc = new Vocabulary(snapshot.getUri());
        when(repositoryService.findRequired(snapshotVoc.getUri())).thenReturn(snapshotVoc);
        when(aclService.findFor(vocabulary)).thenReturn(Optional.of(Generator.generateAccessControlList(false)));
        when(aclService.clone(any())).thenReturn(Generator.generateAccessControlList(false));

        final Snapshot result = sut.createSnapshot(vocabulary);
        assertNotNull(result);
        assertEquals(vocabulary.getUri(), result.getVersionOf());
        verify(snapshotCreator).createSnapshot(vocabulary);
    }

    @Test
    void createSnapshotPublishesVocabularyCreatedEvent() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final SnapshotCreator snapshotCreator = mock(SnapshotCreator.class);
        when(appContext.getBean(SnapshotCreator.class)).thenReturn(snapshotCreator);
        final Snapshot snapshot = Generator.generateSnapshot(vocabulary);
        when(snapshotCreator.createSnapshot(any(Vocabulary.class))).thenReturn(snapshot);
        final Vocabulary snapshotVoc = new Vocabulary(snapshot.getUri());
        when(repositoryService.findRequired(snapshotVoc.getUri())).thenReturn(snapshotVoc);
        when(aclService.findFor(vocabulary)).thenReturn(Optional.of(Generator.generateAccessControlList(false)));
        when(aclService.clone(any())).thenReturn(Generator.generateAccessControlList(false));

        sut.createSnapshot(vocabulary);
        final ArgumentCaptor<VocabularyCreatedEvent> captor = ArgumentCaptor.forClass(VocabularyCreatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertNotNull(captor.getValue());
    }

    @Test
    void getChangesRetrievesChangesForVocabulary() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final List<AbstractChangeRecord> records = Generator.generateChangeRecords(vocabulary,
                                                                                   Generator.generateUserWithId());
        when(changeRecordService.getChanges(vocabulary)).thenReturn(records);
        final List<AbstractChangeRecord> result = sut.getChanges(vocabulary);
        assertEquals(records, result);
        verify(changeRecordService).getChanges(vocabulary);
    }

    @Test
    void persistPublishesVocabularyCreatedEvent() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        when(aclService.createFor(vocabulary)).thenReturn(Generator.generateAccessControlList(false));

        sut.persist(vocabulary);
        verify(repositoryService).persist(vocabulary);
        final ArgumentCaptor<VocabularyCreatedEvent> captor = ArgumentCaptor.forClass(VocabularyCreatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertNotNull(captor.getValue());
    }

    @Test
    void getAccessControlListRetrievesACLForSpecifiedVocabulary() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final AccessControlList acl = Generator.generateAccessControlList(false);
        when(aclService.findForAsDto(vocabulary)).thenReturn(
                Optional.of(Environment.getDtoMapper().accessControlListToDto(acl)));

        final AccessControlListDto result = sut.getAccessControlList(vocabulary);
        assertEquals(acl.getUri(), result.getUri());
        verify(aclService).findForAsDto(vocabulary);
    }

    @Test
    void getAccessControlListThrowsNotFoundExceptionWhenNoACLIsFoundForSpecifiedVocabulary() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        assertThrows(NotFoundException.class, () -> sut.getAccessControlList(vocabulary));
        verify(aclService).findForAsDto(vocabulary);
    }

    @Test
    void addAccessControlRecordRetrievesACLForVocabularyAndAddsSpecifiedRecordToIt() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final AccessControlList acl = Generator.generateAccessControlList(false);
        when(aclService.findFor(vocabulary)).thenReturn(Optional.of(acl));
        final UserAccessControlRecord record = generateAccessControlRecord();

        sut.addAccessControlRecords(vocabulary, record);
        verify(aclService).findFor(vocabulary);
        verify(aclService).addRecord(acl, record);
    }

    @Nonnull
    private UserAccessControlRecord generateAccessControlRecord() {
        final UserAccessControlRecord record = new UserAccessControlRecord();
        record.setHolder(Generator.generateUserWithId());
        record.setAccessLevel(AccessLevel.SECURITY);
        return record;
    }

    @Test
    void removeAccessControlRecordRetrievesACLForVocabularyAndRemovesSpecifiedRecordFromIt() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final AccessControlList acl = Generator.generateAccessControlList(false);
        when(aclService.findFor(vocabulary)).thenReturn(Optional.of(acl));
        final UserAccessControlRecord record = generateAccessControlRecord();
        record.setUri(Generator.generateUri());

        sut.removeAccessControlRecord(vocabulary, record);
        verify(aclService).findFor(vocabulary);
        verify(aclService).removeRecord(acl, record);
    }

    @Test
    void updateAccessControlLevelRetrievesACLForVocabularyAndUpdatesSpecifiedRecord() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final AccessControlList acl = Generator.generateAccessControlList(false);
        when(aclService.findFor(vocabulary)).thenReturn(Optional.of(acl));
        final UserAccessControlRecord record = generateAccessControlRecord();
        record.setUri(Generator.generateUri());

        sut.updateAccessControlLevel(vocabulary, record);
        verify(aclService).findFor(vocabulary);
        verify(aclService).updateRecordAccessLevel(acl, record);
    }

    @Test
    void persistCreatesAccessControlListAndSetsItOnVocabularyInstance() {
        final AccessControlList acl = Generator.generateAccessControlList(true);
        final Vocabulary toPersist = Generator.generateVocabularyWithId();
        when(aclService.createFor(toPersist)).thenReturn(acl);

        sut.persist(toPersist);
        verify(repositoryService).persist(toPersist);
        verify(aclService).createFor(toPersist);
        assertEquals(acl.getUri(), toPersist.getAcl());
    }

    @Test
    void getAccessLevelRetrievesAccessLevelFromVocabularyAuthorizationService() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        when(authorizationService.getAccessLevel(vocabulary)).thenReturn(AccessLevel.READ);

        assertEquals(AccessLevel.READ, sut.getAccessLevel(vocabulary));
        verify(authorizationService).getAccessLevel(vocabulary);
    }

    @Test
    void findRequiredEnhancesResultWithAccessLevelOfCurrentUser() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        when(authorizationService.getAccessLevel(vocabulary)).thenReturn(AccessLevel.WRITE);
        when(repositoryService.findRequired(vocabulary.getUri())).thenReturn(vocabulary);

        final Vocabulary result = sut.findRequired(vocabulary.getUri());
        assertInstanceOf(cz.cvut.kbss.termit.dto.VocabularyDto.class, result);
        assertEquals(AccessLevel.WRITE, ((cz.cvut.kbss.termit.dto.VocabularyDto) result).getAccessLevel());
        verify(repositoryService).findRequired(vocabulary.getUri());
        verify(authorizationService).getAccessLevel(vocabulary);
    }

    @Test
    void findEnhancesResultWithAccessLevelOfCurrentUser() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        when(authorizationService.getAccessLevel(vocabulary)).thenReturn(AccessLevel.WRITE);
        when(repositoryService.find(vocabulary.getUri())).thenReturn(Optional.of(vocabulary));

        final Optional<Vocabulary> result = sut.find(vocabulary.getUri());
        assertTrue(result.isPresent());
        assertInstanceOf(cz.cvut.kbss.termit.dto.VocabularyDto.class, result.get());
        assertEquals(AccessLevel.WRITE, ((cz.cvut.kbss.termit.dto.VocabularyDto) result.get()).getAccessLevel());
        verify(repositoryService).find(vocabulary.getUri());
        verify(authorizationService).getAccessLevel(vocabulary);
    }

    @Test
    void removeRemovesAccessControlList() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        when(repositoryService.findRequired(vocabulary.getUri())).thenReturn(vocabulary);
        final AccessControlList acl = Generator.generateAccessControlList(true);
        when(aclService.findFor(vocabulary)).thenReturn(Optional.of(acl));

        sut.remove(vocabulary);
        verify(repositoryService).remove(vocabulary);
        verify(aclService).remove(acl);
    }

    @Test
    void createSnapshotClonesAccessControlListForNewVocabulary() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final AccessControlList originalAcl = Generator.generateAccessControlList(true);
        when(aclService.findFor(vocabulary)).thenReturn(Optional.of(originalAcl));
        final AccessControlList cloneAcl = Generator.generateAccessControlList(true);
        when(aclService.clone(originalAcl)).thenReturn(cloneAcl);
        final SnapshotCreator snapshotCreator = mock(SnapshotCreator.class);
        when(appContext.getBean(SnapshotCreator.class)).thenReturn(snapshotCreator);
        final Snapshot snapshot = Generator.generateSnapshot(vocabulary);
        final Vocabulary snapshotVoc = Generator.generateVocabulary();
        snapshotVoc.setUri(snapshot.getUri());
        when(repositoryService.findRequired(snapshotVoc.getUri())).thenReturn(snapshotVoc);
        when(snapshotCreator.createSnapshot(any(Vocabulary.class))).thenReturn(snapshot);

        sut.createSnapshot(vocabulary);
        verify(aclService).clone(originalAcl);
        assertEquals(cloneAcl.getUri(), snapshotVoc.getAcl());
    }

    @Test
    void importNewVocabularyCreatesAccessControlListForImportedVocabulary() {
        final MultipartFile fileToImport = new MockMultipartFile("test.ttl", "content to import".getBytes(
                StandardCharsets.UTF_8));
        final Vocabulary persisted = Generator.generateVocabularyWithId();
        when(repositoryService.importVocabulary(anyBoolean(), any(MultipartFile.class))).thenReturn(persisted);
        final AccessControlList acl = Generator.generateAccessControlList(false);
        when(aclService.createFor(any(HasIdentifier.class))).thenReturn(acl);

        sut.importVocabulary(false, fileToImport);
        final InOrder inOrder = inOrder(repositoryService, aclService);
        inOrder.verify(repositoryService).importVocabulary(false, fileToImport);
        inOrder.verify(aclService).createFor(persisted);
        assertEquals(acl.getUri(), persisted.getAcl());
    }

    @Test
    void importNewVocabularyPublishesVocabularyCreatedEvent() {
        final MultipartFile fileToImport = new MockMultipartFile("test.ttl", "content to import".getBytes(
                StandardCharsets.UTF_8));
        final Vocabulary persisted = Generator.generateVocabularyWithId();
        when(repositoryService.importVocabulary(anyBoolean(), any(MultipartFile.class))).thenReturn(persisted);
        final AccessControlList acl = Generator.generateAccessControlList(false);
        when(aclService.createFor(any(HasIdentifier.class))).thenReturn(acl);

        sut.importVocabulary(false, fileToImport);
        final ArgumentCaptor<ApplicationEvent> captor = ArgumentCaptor.forClass(ApplicationEvent.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());
        Optional<VocabularyCreatedEvent> event = captor.getAllValues().stream().filter(e -> e instanceof VocabularyCreatedEvent).map(e->(VocabularyCreatedEvent)e).findAny();
        assertTrue(event.isPresent());
        assertEquals(persisted.getUri(), event.get().getVocabularyIri());
    }

    @Test
    void getExcelTemplateFileReturnsResourceRepresentingExcelTemplateFile() throws Exception {
        when(appContext.getBean(Configuration.class)).thenReturn(new Configuration());
        final TypeAwareResource result = sut.getExcelTemplateFile();
        assertTrue(result.getFileExtension().isPresent());
        assertEquals(ExportFormat.EXCEL.getFileExtension(), result.getFileExtension().get());
        assertTrue(result.getMediaType().isPresent());
        assertEquals(ExportFormat.EXCEL.getMediaType(), result.getMediaType().get());
        final File expectedFile = new File(getClass().getClassLoader().getResource("template/termit-import.xlsx").toURI());
        assertEquals(expectedFile, result.getFile());
    }

    /**
     * The goal for this is to get the results cached and do not force users to wait for validation
     * when they request it.
     */
    @Test
    void publishingVocabularyContentModifiedEventTriggersContentsValidation() {
        final VocabularyContentModifiedEvent event = new VocabularyContentModifiedEvent(this, Generator.generateUri());
        sut.onVocabularyContentModified(event);
        verify(repositoryService).validateContents(event.getVocabularyIri());
    }
}
