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
package cz.cvut.kbss.termit.service.security.authorization.acl;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.model.UserGroup;
import cz.cvut.kbss.termit.model.UserRole;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.acl.AccessControlList;
import cz.cvut.kbss.termit.model.acl.AccessLevel;
import cz.cvut.kbss.termit.model.acl.RoleAccessControlRecord;
import cz.cvut.kbss.termit.model.acl.UserAccessControlRecord;
import cz.cvut.kbss.termit.model.acl.UserGroupAccessControlRecord;
import cz.cvut.kbss.termit.service.business.AccessControlListService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessControlListBasedAuthorizationServiceTest {

    @Mock
    private AccessControlListService aclService;

    @InjectMocks
    private AccessControlListBasedAuthorizationService sut;

    private UserAccount user;

    @BeforeEach
    void setUp() {
        this.user = Generator.generateUserAccount();
    }

    @ParameterizedTest
    @MethodSource("canReadTestArguments")
    void canReadReturnsCorrectResultWhenACLHasUserRecordWithSpecifiedAccessLevel(boolean expected,
                                                                                 AccessLevel accessLevel) {
        final AccessControlList acl = Generator.generateAccessControlList(true);
        final UserAccessControlRecord record = new UserAccessControlRecord(accessLevel, user.toUser());
        record.setUri(Generator.generateUri());
        acl.addRecord(record);
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        when(aclService.findFor(vocabulary)).thenReturn(Optional.of(acl));

        assertEquals(expected, sut.canRead(user, vocabulary));
        verify(aclService).findFor(vocabulary);
    }

    static Stream<Arguments> canReadTestArguments() {
        return Stream.of(
                Arguments.of(false, AccessLevel.NONE),
                Arguments.of(true, AccessLevel.READ),
                Arguments.of(true, AccessLevel.WRITE),
                Arguments.of(true, AccessLevel.SECURITY)
        );
    }

    @ParameterizedTest
    @MethodSource("canReadTestArguments")
    void canReadReturnsCorrectResultWhenACLHasUserGroupRecordContainingUserWithSpecifiedAccessLevel(boolean expected,
                                                                                                    AccessLevel accessLevel) {
        final AccessControlList acl = Generator.generateAccessControlList(true);
        final UserGroup group = Generator.generateUserGroup();
        group.addMember(user.toUser());
        final UserGroupAccessControlRecord record = new UserGroupAccessControlRecord(accessLevel, group);
        record.setUri(Generator.generateUri());
        acl.addRecord(record);
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        when(aclService.findFor(vocabulary)).thenReturn(Optional.of(acl));

        assertEquals(expected, sut.canRead(user, vocabulary));
        verify(aclService).findFor(vocabulary);
    }

    @ParameterizedTest
    @MethodSource("canReadTestArguments")
    void canReadReturnsCorrectResultWhenACLHasRoleRecordWithMatchingRoleWithSpecifiedAccessLevel(boolean expected,
                                                                                                 AccessLevel accessLevel) {
        final AccessControlList acl = Generator.generateAccessControlList(false);
        final UserRole role = new UserRole(URI.create(cz.cvut.kbss.termit.security.model.UserRole.FULL_USER.getType()));
        user.addType(cz.cvut.kbss.termit.security.model.UserRole.FULL_USER.getType());
        final RoleAccessControlRecord record = new RoleAccessControlRecord(accessLevel, role);
        record.setUri(Generator.generateUri());
        acl.addRecord(record);
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        when(aclService.findFor(vocabulary)).thenReturn(Optional.of(acl));

        assertEquals(expected, sut.canRead(user, vocabulary));
        verify(aclService).findFor(vocabulary);
    }

    @Test
    void canReadReturnsTrueWhenSpecifiedUserIsAdmin() {
        user.addType(cz.cvut.kbss.termit.security.model.UserRole.ADMIN.getType());
        assertTrue(sut.canRead(user, Generator.generateVocabularyWithId()));
        verify(aclService, never()).findFor(any());
    }

    @ParameterizedTest
    @MethodSource("canModifyTestArguments")
    void canModifyReturnsCorrectResultWhenACLHasUserRecordWithSpecifiedAccessLevel(boolean expected,
                                                                                   AccessLevel accessLevel) {
        final AccessControlList acl = Generator.generateAccessControlList(true);
        final UserAccessControlRecord record = new UserAccessControlRecord(accessLevel, user.toUser());
        record.setUri(Generator.generateUri());
        acl.addRecord(record);
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        when(aclService.findFor(vocabulary)).thenReturn(Optional.of(acl));

        assertEquals(expected, sut.canModify(user, vocabulary));
        verify(aclService).findFor(vocabulary);
    }

    static Stream<Arguments> canModifyTestArguments() {
        return Stream.of(
                Arguments.of(false, AccessLevel.NONE),
                Arguments.of(false, AccessLevel.READ),
                Arguments.of(true, AccessLevel.WRITE),
                Arguments.of(true, AccessLevel.SECURITY)
        );
    }

    @ParameterizedTest
    @MethodSource("canModifyTestArguments")
    void canModifyReturnsCorrectResultWhenACLHasUserGroupRecordContainingUserWithSpecifiedAccessLevel(boolean expected,
                                                                                                      AccessLevel accessLevel) {
        final AccessControlList acl = Generator.generateAccessControlList(true);
        final UserGroup group = Generator.generateUserGroup();
        group.addMember(user.toUser());
        final UserGroupAccessControlRecord record = new UserGroupAccessControlRecord(accessLevel, group);
        record.setUri(Generator.generateUri());
        acl.addRecord(record);
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        when(aclService.findFor(vocabulary)).thenReturn(Optional.of(acl));

        assertEquals(expected, sut.canModify(user, vocabulary));
        verify(aclService).findFor(vocabulary);
    }

    @ParameterizedTest
    @MethodSource("canModifyTestArguments")
    void canModifyReturnsCorrectResultWhenACLHasRoleRecordWithMatchingRoleWithSpecifiedAccessLevel(boolean expected,
                                                                                                   AccessLevel accessLevel) {
        final AccessControlList acl = Generator.generateAccessControlList(false);
        final UserRole role = new UserRole(URI.create(cz.cvut.kbss.termit.security.model.UserRole.FULL_USER.getType()));
        user.addType(cz.cvut.kbss.termit.security.model.UserRole.FULL_USER.getType());
        final RoleAccessControlRecord record = new RoleAccessControlRecord(accessLevel, role);
        record.setUri(Generator.generateUri());
        acl.addRecord(record);
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        when(aclService.findFor(vocabulary)).thenReturn(Optional.of(acl));

        assertEquals(expected, sut.canModify(user, vocabulary));
        verify(aclService).findFor(vocabulary);
    }

    @Test
    void canModifyReturnsTrueWhenSpecifiedUserIsAdmin() {
        user.addType(cz.cvut.kbss.termit.security.model.UserRole.ADMIN.getType());
        assertTrue(sut.canModify(user, Generator.generateVocabularyWithId()));
        verify(aclService, never()).findFor(any());
    }

    @ParameterizedTest
    @MethodSource("canRemoveTestArguments")
    void canRemoveReturnsCorrectResultWhenACLHasUserRecordWithSpecifiedAccessLevel(boolean expected,
                                                                                   AccessLevel accessLevel) {
        final AccessControlList acl = Generator.generateAccessControlList(true);
        final UserAccessControlRecord record = new UserAccessControlRecord(accessLevel, user.toUser());
        record.setUri(Generator.generateUri());
        acl.addRecord(record);
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        when(aclService.findFor(vocabulary)).thenReturn(Optional.of(acl));

        assertEquals(expected, sut.canRemove(user, vocabulary));
        verify(aclService).findFor(vocabulary);
    }

    static Stream<Arguments> canRemoveTestArguments() {
        return Stream.of(
                Arguments.of(false, AccessLevel.NONE),
                Arguments.of(false, AccessLevel.READ),
                Arguments.of(false, AccessLevel.WRITE),
                Arguments.of(true, AccessLevel.SECURITY)
        );
    }

    @ParameterizedTest
    @MethodSource("canRemoveTestArguments")
    void canRemoveReturnsCorrectResultWhenACLHasUserGroupRecordContainingUserWithSpecifiedAccessLevel(boolean expected,
                                                                                                      AccessLevel accessLevel) {
        final AccessControlList acl = Generator.generateAccessControlList(true);
        final UserGroup group = Generator.generateUserGroup();
        group.addMember(user.toUser());
        final UserGroupAccessControlRecord record = new UserGroupAccessControlRecord(accessLevel, group);
        record.setUri(Generator.generateUri());
        acl.addRecord(record);
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        when(aclService.findFor(vocabulary)).thenReturn(Optional.of(acl));

        assertEquals(expected, sut.canRemove(user, vocabulary));
        verify(aclService).findFor(vocabulary);
    }

    @ParameterizedTest
    @MethodSource("canRemoveTestArguments")
    void canRemoveReturnsCorrectResultWhenACLHasRoleRecordWithMatchingRoleWithSpecifiedAccessLevel(boolean expected,
                                                                                                   AccessLevel accessLevel) {
        final AccessControlList acl = Generator.generateAccessControlList(false);
        final UserRole role = new UserRole(URI.create(cz.cvut.kbss.termit.security.model.UserRole.FULL_USER.getType()));
        user.addType(cz.cvut.kbss.termit.security.model.UserRole.FULL_USER.getType());
        final RoleAccessControlRecord record = new RoleAccessControlRecord(accessLevel, role);
        record.setUri(Generator.generateUri());
        acl.addRecord(record);
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        when(aclService.findFor(vocabulary)).thenReturn(Optional.of(acl));

        assertEquals(expected, sut.canRemove(user, vocabulary));
        verify(aclService).findFor(vocabulary);
    }

    @Test
    void canRemoveReturnsTrueWhenSpecifiedUserIsAdmin() {
        user.addType(cz.cvut.kbss.termit.security.model.UserRole.ADMIN.getType());
        assertTrue(sut.canRemove(user, Generator.generateVocabularyWithId()));
        verify(aclService, never()).findFor(any());
    }

    @Test
    void getAccessLevelReturnsHighestAccessLevelOfSpecifiedUserToTargetResource() {
        final AccessControlList acl = Generator.generateAccessControlList(false);
        final UserRole role = new UserRole(URI.create(cz.cvut.kbss.termit.security.model.UserRole.FULL_USER.getType()));
        user.addType(cz.cvut.kbss.termit.security.model.UserRole.FULL_USER.getType());
        final RoleAccessControlRecord roleRecord = new RoleAccessControlRecord(AccessLevel.READ, role);
        roleRecord.setUri(Generator.generateUri());
        acl.addRecord(roleRecord);
        final UserGroup group = Generator.generateUserGroup();
        group.addMember(user.toUser());
        final UserGroupAccessControlRecord groupRecord = new UserGroupAccessControlRecord(AccessLevel.WRITE, group);
        groupRecord.setUri(Generator.generateUri());
        acl.addRecord(groupRecord);
        final UserAccessControlRecord userRecord = new UserAccessControlRecord(AccessLevel.SECURITY, user.toUser());
        userRecord.setUri(Generator.generateUri());
        acl.addRecord(userRecord);
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        when(aclService.findFor(vocabulary)).thenReturn(Optional.of(acl));

        assertEquals(AccessLevel.SECURITY, sut.getAccessLevel(user, vocabulary));
    }

    @Test
    void getAccessLevelReturnsSecurityForAdmin() {
        user.addType(cz.cvut.kbss.termit.security.model.UserRole.ADMIN.getType());
        assertEquals(AccessLevel.SECURITY, sut.getAccessLevel(user, Generator.generateVocabularyWithId()));
        verify(aclService, never()).findFor(any());
    }

    @Test
    void getAccessLevelReturnsNoneAccessLevelWhenTargetResourceLacksAccessControlList() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        when(aclService.findFor(vocabulary)).thenReturn(Optional.empty());

        assertEquals(AccessLevel.NONE, sut.getAccessLevel(user, vocabulary));
    }

    @ParameterizedTest
    @MethodSource("canReadAnonymouslyTestArguments")
    void canReadAnonymouslyReturnsCorrectResultForAnonymousUserRoleAccessLevel(Boolean canRead, AccessLevel accessLevel) {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final AccessControlList acl = Generator.generateAccessControlList(false);
        final UserRole role = new UserRole(cz.cvut.kbss.termit.security.model.UserRole.ANONYMOUS_USER);
        final RoleAccessControlRecord roleRecord = new RoleAccessControlRecord(accessLevel, role);
        roleRecord.setUri(Generator.generateUri());
        acl.addRecord(roleRecord);
        when(aclService.findFor(vocabulary)).thenReturn(Optional.of(acl));

        assertEquals(canRead, sut.canReadAnonymously(vocabulary));
    }

    static Stream<Arguments> canReadAnonymouslyTestArguments() {
        return Stream.of(
                Arguments.of(false, AccessLevel.NONE),
                Arguments.of(true, AccessLevel.READ)
                // WRITE and SECURITY access levels are not allowed for anonymous users
        );
    }
}
