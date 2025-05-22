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
package cz.cvut.kbss.termit.model.acl;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.model.UserRole;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleAccessControlRecordTest {

    private final UserAccount account = Generator.generateUserAccount();

    @Test
    void getAccessLevelForReturnsAccessLevelWhenSpecifiedUserAccountHasRoleRepresentedByRecord() {
        final UserRole role = new UserRole(URI.create(Vocabulary.s_c_plny_uzivatel_termitu));
        account.addType(Vocabulary.s_c_plny_uzivatel_termitu);
        final RoleAccessControlRecord sut = new RoleAccessControlRecord(AccessLevel.READ, role);

        final Optional<AccessLevel> result = sut.getAccessLevelFor(account);
        assertTrue(result.isPresent());
        assertEquals(sut.getAccessLevel(), result.get());
    }

    @Test
    void getAccessLevelForReturnsEmptyOptionalWhenSpecifiedUserDoesNotHaveRoleRepresentedByRecord() {
        final UserRole role = new UserRole(URI.create(Vocabulary.s_c_administrator_termitu));
        final RoleAccessControlRecord sut = new RoleAccessControlRecord(AccessLevel.WRITE, role);

        final Optional<AccessLevel> result = sut.getAccessLevelFor(account);
        assertFalse(result.isPresent());
    }
}
