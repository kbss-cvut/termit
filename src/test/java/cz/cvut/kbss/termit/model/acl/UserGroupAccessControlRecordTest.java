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
package cz.cvut.kbss.termit.model.acl;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.model.UserGroup;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserGroupAccessControlRecordTest {

    @Test
    void getAccessLevelForReturnsAccessLevelWhenSpecifiedUserIsMemberOfUserGroupRepresentedByRecord() {
        final UserGroup group = new UserGroup();
        final UserAccount account = Generator.generateUserAccount();
        group.addMember(account.toUser());
        group.addMember(Generator.generateUserWithId());
        final UserGroupAccessControlRecord sut = new UserGroupAccessControlRecord(AccessLevel.SECURITY, group);

        final Optional<AccessLevel> result = sut.getAccessLevelFor(account);
        assertTrue(result.isPresent());
        assertEquals(sut.getAccessLevel(), result.get());
    }

    @Test
    void getAccessLevelForReturnsEmptyOptionalWhenSpecifiedMemberIsNotMemberOfUserGroupRepresentedByRecord() {
        final UserGroup group = new UserGroup();
        group.addMember(Generator.generateUserWithId());
        final UserGroupAccessControlRecord sut = new UserGroupAccessControlRecord(AccessLevel.SECURITY, group);

        final Optional<AccessLevel> result = sut.getAccessLevelFor(Generator.generateUserAccount());
        assertFalse(result.isPresent());
    }
}
