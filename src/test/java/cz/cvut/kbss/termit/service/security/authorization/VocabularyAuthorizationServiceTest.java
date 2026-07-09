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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Execution(ExecutionMode.SAME_THREAD)
@ExtendWith(MockitoExtension.class)
class VocabularyAuthorizationServiceTest {

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

    @BeforeEach
    void setUp() {
        Environment.resetCurrentUser();
    }

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
    void canReadChecksIfCurrentUserHasAccessBasedOnAccessControlList() {
        Environment.setCurrentUser(user);
        when(securityUtils.getCurrentUser()).thenReturn(user);
        when(aclBasedAuthService.canRead(user, vocabulary)).thenReturn(true);

        assertTrue(sut.canRead(vocabulary));
        verify(aclBasedAuthService).canRead(user, vocabulary);
    }

    @Test
    void canModifyChecksIfCurrentUserHasAccessBasedOnAccessControlList() {
        when(securityUtils.getCurrentUser()).thenReturn(user);
        when(aclBasedAuthService.canModify(user, vocabulary)).thenReturn(true);

        assertTrue(sut.canModify(vocabulary));
        verify(aclBasedAuthService).canModify(user, vocabulary);
    }

    @Test
    void canRemoveChecksIfCurrentUserHasAccessBasedOnAccessControlList() {
        when(securityUtils.getCurrentUser()).thenReturn(user);
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
    void canCreateSnapshotRequiresCurrentUserToBeAdmin() {
        when(securityUtils.getCurrentUser()).thenReturn(user);
        user.addType(UserRole.ADMIN.getType());
        assertTrue(sut.canCreateSnapshot(vocabulary));
        verify(aclBasedAuthService, never()).hasAccessLevel(any(), eq(user), eq(vocabulary));
    }

    @Test
    void canReimportReturnsTrueWhenVocabularyExistsAndUserHasSecurityAccessBasedOnAccessControlList() {
        when(securityUtils.getCurrentUser()).thenReturn(user);
        when(aclBasedAuthService.hasAccessLevel(AccessLevel.SECURITY, user, vocabulary)).thenReturn(true);
        when(vocabularyRepositoryService.exists(vocabulary.getUri())).thenReturn(true);
        assertTrue(sut.canReimport(vocabulary.getUri()));
        verify(vocabularyRepositoryService).exists(vocabulary.getUri());
        verify(aclBasedAuthService).hasAccessLevel(AccessLevel.SECURITY, user, vocabulary);
    }

    @Test
    void canReimportReturnsFalseWhenVocabularyDoesNotExistAndCurrentUserIsNotEditor() {
        user.addType(UserRole.RESTRICTED_USER.getType());
        when(securityUtils.getCurrentUser()).thenReturn(user);
        when(vocabularyRepositoryService.exists(vocabulary.getUri())).thenReturn(false);

        assertFalse(sut.canReimport(vocabulary.getUri()));
        verify(aclBasedAuthService, never()).hasAccessLevel(any(), any(), any());
    }

    @Test
    void canReimportReturnsTrueWhenVocabularyDoesNotExistAndCurrentUserIsEditor() {
        user.addType(UserRole.FULL_USER.getType());
        when(securityUtils.getCurrentUser()).thenReturn(user);
        when(vocabularyRepositoryService.exists(vocabulary.getUri())).thenReturn(false);

        assertTrue(sut.canReimport(vocabulary.getUri()));
        verify(aclBasedAuthService, never()).hasAccessLevel(any(), any(), any());
    }

    @Test
    void canRemoveFilesReturnsTrueWhenVocabularyExistsAndUserHasSecurityAccessBasedOnAccessControlList() {
        when(securityUtils.getCurrentUser()).thenReturn(user);
        when(aclBasedAuthService.hasAccessLevel(AccessLevel.SECURITY, user, vocabulary)).thenReturn(true);

        assertTrue(sut.canRemoveFiles(vocabulary));
        verify(aclBasedAuthService).hasAccessLevel(AccessLevel.SECURITY, user, vocabulary);
    }

    @Test
    void canRemoveSnapshotRequiresCurrentUserToBeAdmin() {
        when(securityUtils.getCurrentUser()).thenReturn(user);
        user.addType(UserRole.ADMIN.getType());
        assertTrue(sut.canRemoveSnapshot(vocabulary));
        verify(aclBasedAuthService, never()).hasAccessLevel(any(), eq(user), eq(vocabulary));
    }

    @Test
    void getAccessLevelRetrievesCurrentUsersAccessLevelBasedOnAccessControlList() {
        Environment.setCurrentUser(user);
        when(securityUtils.getCurrentUser()).thenReturn(user);
        when(aclBasedAuthService.getAccessLevel(user, vocabulary)).thenReturn(AccessLevel.WRITE);

        assertEquals(AccessLevel.WRITE, sut.getAccessLevel(vocabulary));
        verify(aclBasedAuthService).getAccessLevel(user, vocabulary);
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
