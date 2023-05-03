package cz.cvut.kbss.termit.service.security.authorization;

import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.acl.AccessLevel;
import cz.cvut.kbss.termit.security.model.UserRole;
import cz.cvut.kbss.termit.service.repository.VocabularyRepositoryService;
import cz.cvut.kbss.termit.service.security.SecurityUtils;
import cz.cvut.kbss.termit.service.security.authorization.acl.AccessControlListBasedAuthorizationService;
import cz.cvut.kbss.termit.workspace.EditableVocabularies;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VocabularyAuthorizationServiceTest {

    @Mock
    private EditableVocabularies editableVocabularies;

    @Mock
    private AccessControlListBasedAuthorizationService aclBasedAuthService;

    @Mock
    private VocabularyRepositoryService vocabularyRepositoryService;

    @Mock
    private SecurityUtils securityUtils;

    @InjectMocks
    private VocabularyAuthorizationService sut;

    private final UserAccount user = Generator.generateUserAccount();

    private final Vocabulary vocabulary = Generator.generateVocabularyWithId();

    @AfterEach
    void tearDown() {
        Environment.resetCurrentUser();
    }

    @ParameterizedTest
    @MethodSource("getCanCreateResultAndParams")
    void canCreateRequiresAtLeastEditorUser(boolean expected, UserRole role) {
        user.addType(role.getType());
        when(securityUtils.getCurrentUser()).thenReturn(user);
        assertEquals(expected, sut.canCreate());
    }

    static Stream<Arguments> getCanCreateResultAndParams() {
        return Stream.of(
                Arguments.of(false, UserRole.RESTRICTED_USER),
                Arguments.of(true, UserRole.FULL_USER),
                Arguments.of(true, UserRole.ADMIN)
        );
    }

    @Test
    void canReadChecksIfVocabularyIsInCurrentWorkspace() {
        Environment.setCurrentUser(user);
        when(securityUtils.getCurrentUser()).thenReturn(user);
        when(editableVocabularies.isEditable(vocabulary)).thenReturn(true);
        when(aclBasedAuthService.canRead(user, vocabulary)).thenReturn(true);

        assertTrue(sut.canRead(vocabulary));
        verify(editableVocabularies).isEditable(vocabulary);
    }

    @Test
    void canReadChecksIfCurrentUserHasAccessBasedOnAccessControlList() {
        Environment.setCurrentUser(user);
        when(securityUtils.getCurrentUser()).thenReturn(user);
        when(editableVocabularies.isEditable(vocabulary)).thenReturn(true);
        when(aclBasedAuthService.canRead(user, vocabulary)).thenReturn(true);

        assertTrue(sut.canRead(vocabulary));
        verify(aclBasedAuthService).canRead(user, vocabulary);
    }

    @Test
    void canModifyChecksIfVocabularyIsEditableInCurrentWorkspace() {
        when(securityUtils.getCurrentUser()).thenReturn(user);
        when(editableVocabularies.isEditable(vocabulary)).thenReturn(true);
        when(aclBasedAuthService.canModify(user, vocabulary)).thenReturn(true);

        assertTrue(sut.canModify(vocabulary));
        verify(editableVocabularies).isEditable(vocabulary);
    }

    @Test
    void canModifyChecksIfCurrentUserHasAccessBasedOnAccessControlList() {
        when(securityUtils.getCurrentUser()).thenReturn(user);
        when(editableVocabularies.isEditable(vocabulary)).thenReturn(true);
        when(aclBasedAuthService.canModify(user, vocabulary)).thenReturn(true);

        assertTrue(sut.canModify(vocabulary));
        verify(aclBasedAuthService).canModify(user, vocabulary);
    }

    @Test
    void canRemoveChecksIfVocabularyIsEditableInCurrentWorkspace() {
        when(securityUtils.getCurrentUser()).thenReturn(user);
        when(editableVocabularies.isEditable(vocabulary)).thenReturn(true);
        when(aclBasedAuthService.canRemove(user, vocabulary)).thenReturn(true);

        assertTrue(sut.canRemove(vocabulary));
        verify(editableVocabularies).isEditable(vocabulary);
    }

    @Test
    void canRemoveChecksIfCurrentUserHasAccessBasedOnAccessControlList() {
        when(securityUtils.getCurrentUser()).thenReturn(user);
        when(editableVocabularies.isEditable(vocabulary)).thenReturn(true);
        when(aclBasedAuthService.canRemove(user, vocabulary)).thenReturn(true);

        assertTrue(sut.canRemove(vocabulary));
        verify(aclBasedAuthService).canRemove(user, vocabulary);
    }

    @Test
    void canManageAccessChecksIfCurrentUserHasSecurityAccessBasedOnAccessControlList() {
        when(securityUtils.getCurrentUser()).thenReturn(user);
        when(aclBasedAuthService.hasAccessLevel(AccessLevel.SECURITY, user, vocabulary)).thenReturn(true);

        assertTrue(sut.canManageAccess(vocabulary));
        verify(aclBasedAuthService).hasAccessLevel(AccessLevel.SECURITY, user, vocabulary);
    }

    @Test
    void canCreateSnapshotChecksIfCurrentUserHasSecurityAccessBasedOnAccessControlList() {
        when(securityUtils.getCurrentUser()).thenReturn(user);
        when(aclBasedAuthService.hasAccessLevel(AccessLevel.SECURITY, user, vocabulary)).thenReturn(false);

        assertFalse(sut.canCreateSnapshot(vocabulary));
        verify(aclBasedAuthService).hasAccessLevel(AccessLevel.SECURITY, user, vocabulary);
    }

    @Test
    void canCreateSnapshotChecksIfVocabularyIsEditableInCurrentWorkspace() {
        when(securityUtils.getCurrentUser()).thenReturn(user);
        when(aclBasedAuthService.hasAccessLevel(AccessLevel.SECURITY, user, vocabulary)).thenReturn(true);
        when(editableVocabularies.isEditable(vocabulary)).thenReturn(true);

        assertTrue(sut.canCreateSnapshot(vocabulary));
        verify(editableVocabularies).isEditable(vocabulary);
    }

    @Test
    void canReimportReturnsTrueWhenVocabularyExistsAndUserHasSecurityAccessBasedOnAccessControlList() {
        when(securityUtils.getCurrentUser()).thenReturn(user);
        when(aclBasedAuthService.hasAccessLevel(AccessLevel.SECURITY, user, vocabulary)).thenReturn(true);
        when(vocabularyRepositoryService.exists(vocabulary.getUri())).thenReturn(true);
        when(editableVocabularies.isEditable(vocabulary)).thenReturn(true);

        assertTrue(sut.canReimport(vocabulary.getUri()));
        verify(vocabularyRepositoryService).exists(vocabulary.getUri());
        verify(aclBasedAuthService).hasAccessLevel(AccessLevel.SECURITY, user, vocabulary);
    }

    @Test
    void canReimportChecksIfVocabularyWithSpecifiedIriIsEditableInCurrentWorkspace() {
        when(securityUtils.getCurrentUser()).thenReturn(user);
        when(aclBasedAuthService.hasAccessLevel(AccessLevel.SECURITY, user, vocabulary)).thenReturn(true);
        when(vocabularyRepositoryService.exists(vocabulary.getUri())).thenReturn(true);
        when(editableVocabularies.isEditable(vocabulary)).thenReturn(true);

        assertTrue(sut.canReimport(vocabulary.getUri()));
        verify(editableVocabularies).isEditable(vocabulary);
    }

    @Test
    void canReimportReturnsFalseWhenVocabularyDoesNotExistAndCurrentUserIsNotEditor() {
        user.addType(UserRole.RESTRICTED_USER.getType());
        when(securityUtils.getCurrentUser()).thenReturn(user);
        when(vocabularyRepositoryService.exists(vocabulary.getUri())).thenReturn(false);

        assertFalse(sut.canReimport(vocabulary.getUri()));
        verify(aclBasedAuthService, never()).hasAccessLevel(any(), any(), any());
        verify(editableVocabularies, never()).isEditable(any(Vocabulary.class));
    }

    @Test
    void canReimportReturnsTrueWhenVocabularyDoesNotExistAndCurrentUserIsEditor() {
        user.addType(UserRole.FULL_USER.getType());
        when(securityUtils.getCurrentUser()).thenReturn(user);
        when(vocabularyRepositoryService.exists(vocabulary.getUri())).thenReturn(false);

        assertTrue(sut.canReimport(vocabulary.getUri()));
        verify(aclBasedAuthService, never()).hasAccessLevel(any(), any(), any());
        verify(editableVocabularies, never()).isEditable(any(Vocabulary.class));
    }

    @Test
    void canRemoveFilesReturnsTrueWhenVocabularyExistsAndUserHasSecurityAccessBasedOnAccessControlList() {
        when(securityUtils.getCurrentUser()).thenReturn(user);
        when(aclBasedAuthService.hasAccessLevel(AccessLevel.SECURITY, user, vocabulary)).thenReturn(true);

        assertTrue(sut.canRemoveFiles(vocabulary));
        verify(aclBasedAuthService).hasAccessLevel(AccessLevel.SECURITY, user, vocabulary);
    }

    @Test
    void canRemoveSnapshotChecksIfCurrentUserHasSecurityAccessBasedOnAccessControlList() {
        when(securityUtils.getCurrentUser()).thenReturn(user);
        when(aclBasedAuthService.hasAccessLevel(AccessLevel.SECURITY, user, vocabulary)).thenReturn(false);

        assertFalse(sut.canRemoveSnapshot(vocabulary));
        verify(aclBasedAuthService).hasAccessLevel(AccessLevel.SECURITY, user, vocabulary);
    }

    @Test
    void canRemoveSnapshotChecksIfVocabularyIsEditableInCurrentWorkspace() {
        when(securityUtils.getCurrentUser()).thenReturn(user);
        when(aclBasedAuthService.hasAccessLevel(AccessLevel.SECURITY, user, vocabulary)).thenReturn(true);
        when(editableVocabularies.isEditable(vocabulary)).thenReturn(true);

        assertTrue(sut.canRemoveSnapshot(vocabulary));
        verify(editableVocabularies).isEditable(vocabulary);
    }

    @Test
    void getAccessLevelRetrievesCurrentUsersAccessLevelBasedOnAccessControlList() {
        Environment.setCurrentUser(user);
        when(securityUtils.getCurrentUser()).thenReturn(user);
        when(aclBasedAuthService.getAccessLevel(user, vocabulary)).thenReturn(AccessLevel.WRITE);
        when(editableVocabularies.isEditable(vocabulary)).thenReturn(true);

        assertEquals(AccessLevel.WRITE, sut.getAccessLevel(vocabulary));
        verify(aclBasedAuthService).getAccessLevel(user, vocabulary);
    }

    @Test
    void getAccessLevelReturnsNoneWhenVocabularyIsNotEditableInCurrentWorkspace() {
        Environment.setCurrentUser(user);
        when(securityUtils.getCurrentUser()).thenReturn(user);
        when(editableVocabularies.isEditable(vocabulary)).thenReturn(false);

        assertEquals(AccessLevel.NONE, sut.getAccessLevel(vocabulary));
    }

    @Test
    void canReadChecksForAnonymousReadPermissionsWhenUserIsNotLoggedIn() {
        assertFalse(sut.canRead(vocabulary));
        verify(aclBasedAuthService).canReadAnonymously(vocabulary);
    }

    @Test
    void getAccessLevelReturnsNoneWhenUserIsNotLoggedInAndAnonymousReadAccessIsNotAuthorized() {
        assertEquals(AccessLevel.NONE, sut.getAccessLevel(vocabulary));
        verify(aclBasedAuthService).canReadAnonymously(vocabulary);
    }

    @Test
    void getAccessLevelReturnsReadWhenUserIsNotLoggedInAndAnonymousReadAccessIsAuthorized() {
        when(aclBasedAuthService.canReadAnonymously(vocabulary)).thenReturn(true);
        assertEquals(AccessLevel.READ, sut.getAccessLevel(vocabulary));
        verify(aclBasedAuthService).canReadAnonymously(vocabulary);
    }
}
