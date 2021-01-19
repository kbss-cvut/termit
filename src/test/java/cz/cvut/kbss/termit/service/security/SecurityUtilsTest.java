/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If not, see
 * <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.service.security;

import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;

class SecurityUtilsTest extends BaseServiceTestRunner {

    @Autowired
    private SecurityUtils sut;

    private UserAccount user;

    @BeforeEach
    void setUp() {
        this.user = Generator.generateUserAccountWithPassword();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentUserReturnsCurrentlyLoggedInUser() {
        Environment.setCurrentUser(user);
        final UserAccount result = sut.getCurrentUser();
        assertEquals(user, result);
    }

    @Test
    void isAuthenticatedReturnsFalseForUnauthenticatedUser() {
        assertFalse(sut.isAuthenticated());
    }

    @Test
    void isAuthenticatedReturnsTrueForAuthenticatedUser() {
        Environment.setCurrentUser(user);
        assertTrue(sut.isAuthenticated());
    }

    @Test
    void isAuthenticatedReturnsFalseForEmptyContext() {
        assertFalse(sut.isAuthenticated());
    }

    @Test
    void isAuthenticatedWorksInStaticVersion() {
        Environment.setCurrentUser(user);
        assertTrue(SecurityUtils.authenticated());
    }
}
