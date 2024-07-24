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
package cz.cvut.kbss.termit.security.model;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TermItUserDetailsTest {

    @Test
    void constructorInitializesDefaultUserAuthority() {
        final UserAccount user = Generator.generateUserAccount();
        final TermItUserDetails result = new TermItUserDetails(user);
        assertEquals(1, result.getAuthorities().size());
        assertTrue(result.getAuthorities().contains(new SimpleGrantedAuthority(UserRole.RESTRICTED_USER.getName())));
    }

    @Test
    void authorityBasedConstructorAddsDefaultAuthority() {
        final Set<GrantedAuthority> authorities = Collections.singleton(new SimpleGrantedAuthority("ROLE_MANAGER"));
        final TermItUserDetails result = new TermItUserDetails(Generator.generateUserAccount(), authorities);
        assertTrue(result.getAuthorities().contains(new SimpleGrantedAuthority(UserRole.RESTRICTED_USER.getName())));
    }

    @Test
    void constructorResolvesAuthoritiesFromUserTypes() {
        final UserAccount user = Generator.generateUserAccount();
        user.addType(Vocabulary.s_c_administrator_termitu);
        final TermItUserDetails result = new TermItUserDetails(user);
        assertEquals(3, result.getAuthorities().size());
        assertTrue(result.getAuthorities().contains(new SimpleGrantedAuthority(UserRole.RESTRICTED_USER.getName())));
        assertTrue(result.getAuthorities().contains(new SimpleGrantedAuthority(UserRole.FULL_USER.getName())));
        assertTrue(result.getAuthorities().contains(new SimpleGrantedAuthority(UserRole.ADMIN.getName())));
    }

    @Test
    void authorityBasedConstructorResolvesAuthoritiesFromUserTypes() {
        final Set<GrantedAuthority> authorities = Collections.singleton(new SimpleGrantedAuthority("ROLE_MANAGER"));
        final UserAccount user = Generator.generateUserAccount();
        user.addType(Vocabulary.s_c_administrator_termitu);
        final TermItUserDetails result = new TermItUserDetails(user, authorities);
        assertEquals(4, result.getAuthorities().size());
        assertTrue(result.getAuthorities().contains(new SimpleGrantedAuthority(UserRole.RESTRICTED_USER.getName())));
        assertTrue(result.getAuthorities().contains(new SimpleGrantedAuthority(UserRole.FULL_USER.getName())));
        assertTrue(result.getAuthorities().contains(new SimpleGrantedAuthority(UserRole.ADMIN.getName())));
        assertTrue(result.getAuthorities().containsAll(authorities));
    }

    @Test
    void getUserReturnsCopyOfUser() {
        final UserAccount user = Generator.generateUserAccount();
        final TermItUserDetails sut = new TermItUserDetails(user);
        final UserAccount result = sut.getUser();
        assertEquals(user, result);
        assertNotSame(user, result);
    }
}