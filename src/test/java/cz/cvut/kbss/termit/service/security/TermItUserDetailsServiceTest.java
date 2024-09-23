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
package cz.cvut.kbss.termit.service.security;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.security.model.TermItUserDetails;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TermItUserDetailsServiceTest extends BaseServiceTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private TermItUserDetailsService sut;

    @Test
    void loadUserByUsernameReturnsUserDetailsForLoadedUser() {
        final UserAccount user = Generator.generateUserAccountWithPassword();
        transactional(() -> em.persist(user));

        final TermItUserDetails result = sut.loadUserByUsername(user.getUsername());
        assertNotNull(result);
        assertEquals(user, result.getUser());
    }

    @Test
    void loadUserByUsernameThrowsUsernameNotFoundForUnknownUsername() {
        final String username = "unknownUsername";
        final UsernameNotFoundException ex =
                assertThrows(UsernameNotFoundException.class, () -> sut.loadUserByUsername(username));
        assertEquals("User with username " + username + " not found.", ex.getMessage());
    }
}
