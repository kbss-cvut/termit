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
package cz.cvut.kbss.termit.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.security.model.AuthenticationToken;
import cz.cvut.kbss.termit.security.model.LoginStatus;
import cz.cvut.kbss.termit.security.model.TermItUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.*;

@Tag("security")
class AuthenticationSuccessTest {

    private final UserAccount person = Generator.generateUserAccount();

    private ObjectMapper mapper;

    private AuthenticationSuccess sut;

    @BeforeEach
    void setUp() {
        this.mapper = Environment.getObjectMapper();
        this.sut = new AuthenticationSuccess(mapper);
    }

    @Test
    void authenticationSuccessReturnsResponseContainingUsername() throws Exception {
        final MockHttpServletResponse response = response();
        sut.onAuthenticationSuccess(request(), response, generateAuthenticationToken());
        verifyLoginStatus(response, person.getUsername());
    }

    private void verifyLoginStatus(MockHttpServletResponse response, String expectedUsername)
            throws java.io.IOException {
        final LoginStatus status = mapper.readValue(response.getContentAsString(), LoginStatus.class);
        assertTrue(status.isSuccess());
        assertTrue(status.isLoggedIn());
        assertEquals(expectedUsername, status.getUsername());
        assertNull(status.getErrorMessage());
    }

    static MockHttpServletRequest request() {
        final MockHttpServletRequest req = new MockHttpServletRequest();
        req.setSession(new MockHttpSession());
        return req;
    }

    static MockHttpServletResponse response() {
        return new MockHttpServletResponse();
    }

    private Authentication generateAuthenticationToken() {
        final TermItUserDetails userDetails = new TermItUserDetails(person);
        return new AuthenticationToken(userDetails.getAuthorities(), userDetails);
    }

    @Test
    void logoutSuccessReturnsResponseContainingLoginStatus() throws Exception {
        final MockHttpServletResponse response = response();
        sut.onLogoutSuccess(request(), response, generateAuthenticationToken());
        final LoginStatus status = mapper.readValue(response.getContentAsString(), LoginStatus.class);
        assertTrue(status.isSuccess());
        assertFalse(status.isLoggedIn());
        assertNull(status.getUsername());
        assertNull(status.getErrorMessage());
    }
}
