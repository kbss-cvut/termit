package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.UserRole;
import cz.cvut.kbss.termit.model.acl.*;
import cz.cvut.kbss.termit.persistence.dao.acl.AccessControlListDao;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.Collections;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RepositoryAccessControlListServiceTest {

    @Mock
    private AccessControlListDao dao;

    @InjectMocks
    private RepositoryAccessControlListService sut;

    @Test
    void addRecordsLoadsTargetAccessControlListAddsSpecifiedRecordsToItAndUpdatesIt() {
        final AccessControlList acl = generateAcl();
        final AccessControlRecord<UserRole> toAdd = new RoleAccessControlRecord();
        toAdd.setAccessLevel(AccessLevel.READ);
        toAdd.setHolder(new UserRole());
        toAdd.getHolder().setUri(URI.create(Vocabulary.s_c_omezeny_uzivatel_termitu));

        sut.addRecords(acl, Collections.singleton(toAdd));
        verify(dao).find(acl.getUri());
        final ArgumentCaptor<AccessControlList> captor = ArgumentCaptor.forClass(AccessControlList.class);
        verify(dao).update(captor.capture());
        assertEquals(acl.getUri(), captor.getValue().getUri());
        assertThat(captor.getValue().getRecords(), hasItem(toAdd));
    }

    private AccessControlList generateAcl() {
        final AccessControlList acl = new AccessControlList();
        acl.setUri(Generator.generateUri());
        when(dao.find(acl.getUri())).thenReturn(Optional.of(acl));
        return acl;
    }

    @Test
    void addRecordsDoesNothingWhenNoRecordsAreProvidedForAddition() {
        final AccessControlList acl = new AccessControlList();
        acl.setUri(Generator.generateUri());

        sut.addRecords(acl, Collections.emptyList());
        verify(dao, never()).find(any());
        verify(dao, never()).update(any());
    }

    @Test
    void removeRecordsLoadsTargetAccessControlListRemovesSpecifiedRecordsAndUpdatesIt() {
        final AccessControlList acl = generateAcl();
        final UserAccessControlRecord existingRecord = new UserAccessControlRecord();
        existingRecord.setUri(Generator.generateUri());
        existingRecord.setHolder(Generator.generateUserWithId());
        acl.addRecord(existingRecord);
        final UserGroupAccessControlRecord toRemove = new UserGroupAccessControlRecord();
        toRemove.setUri(Generator.generateUri());
        toRemove.setHolder(Generator.generateUserGroup());
        acl.addRecord(toRemove);

        sut.removeRecords(acl, Collections.singletonList(toRemove));
        verify(dao).find(acl.getUri());
        final ArgumentCaptor<AccessControlList> captor = ArgumentCaptor.forClass(AccessControlList.class);
        verify(dao).update(captor.capture());
        assertEquals(acl.getUri(), captor.getValue().getUri());
        assertThat(captor.getValue().getRecords(), not(hasItem(toRemove)));
    }

    @Test
    void removeRecordsDoesNothingWhenNoRecordsAreProvidedForRemoval() {
        final AccessControlList acl = new AccessControlList();
        acl.setUri(Generator.generateUri());

        sut.removeRecords(acl, Collections.emptyList());
        verify(dao, never()).find(any());
        verify(dao, never()).update(any());
    }

    @Test
    void updateRecordsLoadsTargetAccessControlListAndUpdatesSpecifiedRecords() {
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
}
