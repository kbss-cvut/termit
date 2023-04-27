package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.dto.acl.AccessControlListDto;
import cz.cvut.kbss.termit.dto.listing.TermDto;
import cz.cvut.kbss.termit.dto.listing.VocabularyDto;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.event.VocabularyCreatedEvent;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.acl.AccessControlList;
import cz.cvut.kbss.termit.model.acl.AccessLevel;
import cz.cvut.kbss.termit.model.acl.UserAccessControlRecord;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.persistence.context.VocabularyContextMapper;
import cz.cvut.kbss.termit.persistence.snapshot.SnapshotCreator;
import cz.cvut.kbss.termit.service.business.AccessControlListService;
import cz.cvut.kbss.termit.service.business.VocabularyService;
import cz.cvut.kbss.termit.service.business.async.AsyncTermService;
import cz.cvut.kbss.termit.service.security.authorization.VocabularyAuthorizationService;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;

import java.util.*;

import static cz.cvut.kbss.termit.environment.Environment.termsToDtos;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VocabularyServiceTest {

    @Mock
    private AsyncTermService termService;

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
        sut.runTextAnalysisOnAllTerms(vocabulary);
        verify(termService).asyncAnalyzeTermDefinitions(Map.of(termOne, vocabulary.getUri(),
                                                               termTwo, vocabulary.getUri()));
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
        verify(termService).asyncAnalyzeTermDefinitions(Map.of(term, v.getUri()));
    }

    @Test
    void createSnapshotCreatesSnapshotOfSpecifiedVocabulary() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final SnapshotCreator snapshotCreator = mock(SnapshotCreator.class);
        when(appContext.getBean(SnapshotCreator.class)).thenReturn(snapshotCreator);
        when(snapshotCreator.createSnapshot(any(Vocabulary.class))).thenReturn(Generator.generateSnapshot(vocabulary));

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
        when(snapshotCreator.createSnapshot(any(Vocabulary.class))).thenReturn(Generator.generateSnapshot(vocabulary));

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
        when(aclService.findForAsDto(vocabulary)).thenReturn(Optional.of(Environment.getDtoMapper().accessControlListToDto(acl)));

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

    @NotNull
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
        final Vocabulary toPersist = Generator.generateVocabulary();
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
}
