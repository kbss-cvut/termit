package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.exception.UnsupportedOperationException;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.model.UserRole;
import cz.cvut.kbss.termit.model.acl.*;
import cz.cvut.kbss.termit.persistence.dao.acl.AccessControlListDao;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepositoryAccessControlListServiceTest {

    @Mock
    private AccessControlListDao dao;

    @Mock
    private ChangeRecordService changeRecordService;

    @Mock
    private UserRoleRepositoryService userRoleService;

    @Spy
    private Configuration configuration = new Configuration();

    @InjectMocks
    private RepositoryAccessControlListService sut;

    @Test
    void addRecordLoadsTargetAccessControlListAddsSpecifiedRecordToItAndUpdatesIt() {
        final AccessControlList acl = generateAcl();
        final AccessControlRecord<UserRole> toAdd = new RoleAccessControlRecord();
        toAdd.setAccessLevel(AccessLevel.READ);
        toAdd.setHolder(new UserRole());
        toAdd.getHolder().setUri(URI.create(Vocabulary.s_c_omezeny_uzivatel_termitu));

        sut.addRecord(acl, toAdd);
        verify(dao).find(acl.getUri());
        final ArgumentCaptor<AccessControlList> captor = ArgumentCaptor.forClass(AccessControlList.class);
        verify(dao).update(captor.capture());
        assertEquals(acl.getUri(), captor.getValue().getUri());
        assertThat(captor.getValue().getRecords(), hasItem(toAdd));
    }

    private AccessControlList generateAcl() {
        final AccessControlList acl = Generator.generateAccessControlList(false);
        when(dao.find(acl.getUri())).thenReturn(Optional.of(acl));
        return acl;
    }

    @Test
    void removeRecordLoadsTargetAccessControlListRemovesSpecifiedRecordsAndUpdatesIt() {
        final AccessControlList acl = generateAcl();
        acl.addRecord(new RoleAccessControlRecord(AccessLevel.READ, new UserRole(cz.cvut.kbss.termit.security.model.UserRole.RESTRICTED_USER)));
        acl.addRecord(new RoleAccessControlRecord(AccessLevel.WRITE, new UserRole(cz.cvut.kbss.termit.security.model.UserRole.FULL_USER)));
        final UserAccessControlRecord existingRecord = new UserAccessControlRecord();
        existingRecord.setUri(Generator.generateUri());
        existingRecord.setHolder(Generator.generateUserWithId());
        acl.addRecord(existingRecord);
        final UserGroupAccessControlRecord toRemove = new UserGroupAccessControlRecord();
        toRemove.setUri(Generator.generateUri());
        toRemove.setHolder(Generator.generateUserGroup());
        acl.addRecord(toRemove);

        sut.removeRecord(acl, toRemove);
        verify(dao).find(acl.getUri());
        final ArgumentCaptor<AccessControlList> captor = ArgumentCaptor.forClass(AccessControlList.class);
        verify(dao).update(captor.capture());
        assertEquals(acl.getUri(), captor.getValue().getUri());
        assertThat(captor.getValue().getRecords(), not(hasItem(toRemove)));
    }

    @Test
    void updateRecordLoadsTargetAccessControlListAndUpdatesSpecifiedRecords() {
        final AccessControlList acl = generateAcl();
        final UserAccessControlRecord existingRecord = new UserAccessControlRecord();
        existingRecord.setUri(Generator.generateUri());
        existingRecord.setHolder(Generator.generateUserWithId());
        existingRecord.setAccessLevel(AccessLevel.READ);
        acl.addRecord(existingRecord);

        final UserAccessControlRecord update = new UserAccessControlRecord();
        update.setUri(existingRecord.getUri());
        update.setHolder(existingRecord.getHolder());
        update.setAccessLevel(AccessLevel.WRITE);
        sut.updateRecordAccessLevel(acl, update);

        assertEquals(1, acl.getRecords().size());
        assertEquals(update.getAccessLevel(), acl.getRecords().iterator().next().getAccessLevel());
    }

    @Test
    void getRequiredReferenceThrowsNotFoundExceptionWhenMatchingAccessControlListIsNotFound() {
        final URI uri = Generator.generateUri();
        when(dao.getReference(uri)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> sut.getRequiredReference(uri));
        verify(dao).getReference(uri);
    }

    @Test
    void findRequiredThrowsNotFoundExceptionWhenMatchingAccessControlListIsNotFound() {
        final URI uri = Generator.generateUri();
        when(dao.find(uri)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> sut.findRequired(uri));
        verify(dao).find(uri);
    }

    @Test
    void createForAddsUserAccessLevelRecordWithSecurityLevelForCurrentUser() {
        final cz.cvut.kbss.termit.model.Vocabulary subject = Generator.generateVocabularyWithId();
        final UserAccount current = Generator.generateUserAccount();
        Environment.setCurrentUser(current);

        final AccessControlList result = sut.createFor(subject);
        assertNotNull(result);
        assertThat(result.getRecords(), hasItem(new UserAccessControlRecord(AccessLevel.SECURITY, current.toUser())));
        verify(dao).persist(result);
    }

    @Test
    void createForAddsUserAccessLevelRecordsWithSecurityLevelForAuthorsResolvedFromPersistChangeRecords() {
        // Intentionally not setting current user to simulate the situation when generation of ACLs is triggered
        // automatically for existing vocabularies that do not have it
        final cz.cvut.kbss.termit.model.Vocabulary subject = Generator.generateVocabularyWithId();
        final User author = Generator.generateUserWithId();
        final User authorTwo = Generator.generateUserWithId();
        when(changeRecordService.getAuthors(subject)).thenReturn(Set.of(author, authorTwo));

        final AccessControlList result = sut.createFor(subject);
        assertThat(result.getRecords(), hasItems(
                new UserAccessControlRecord(AccessLevel.SECURITY, author),
                new UserAccessControlRecord(AccessLevel.SECURITY, authorTwo)
        ));
        verify(dao).persist(result);
    }

    @Test
    void createForAddsRoleAccessLevelRecordsForReaderAndEditorBasedOnConfiguration() {
        final cz.cvut.kbss.termit.model.Vocabulary subject = Generator.generateVocabularyWithId();
        Environment.setCurrentUser(Generator.generateUserAccount());
        final UserRole editor = new UserRole(cz.cvut.kbss.termit.security.model.UserRole.FULL_USER);
        final UserRole reader = new UserRole(cz.cvut.kbss.termit.security.model.UserRole.RESTRICTED_USER);
        when(userRoleService.findAll()).thenReturn(List.of(reader, editor));

        final AccessControlList result = sut.createFor(subject);
        assertThat(result.getRecords(), hasItems(
                new RoleAccessControlRecord(configuration.getAcl().getDefaultEditorAccessLevel(), editor),
                new RoleAccessControlRecord(configuration.getAcl().getDefaultReaderAccessLevel(), reader)
        ));
        verify(dao).persist(result);
    }

    @Test
    void removeRecordThrowsUnsupportedOperationExceptionWhenAttemptingToRemoveRoleRecord() {
        final AccessControlList acl = generateAcl();
        final RoleAccessControlRecord toRemove = new RoleAccessControlRecord(AccessLevel.WRITE, new UserRole(cz.cvut.kbss.termit.security.model.UserRole.FULL_USER));
        acl.addRecord(toRemove);

        assertThrows(UnsupportedOperationException.class, () -> sut.removeRecord(acl, toRemove));
    }
}
