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
package cz.cvut.kbss.termit.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.security.model.LoginStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static cz.cvut.kbss.termit.security.AuthenticationSuccessTest.request;
import static cz.cvut.kbss.termit.security.AuthenticationSuccessTest.response;
import static org.junit.jupiter.api.Assertions.*;

@Tag("security")
class AuthenticationFailureTest {

    private AuthenticationFailure sut;

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        this.mapper = Environment.getObjectMapper();
        this.sut = new AuthenticationFailure(mapper);
    }

    @Test
    void authenticationFailureReturnsLoginStatusWithErrorInfoOnUsernameNotFound() throws Exception {
        final MockHttpServletRequest request = request();
        final MockHttpServletResponse response = response();
        final String msg = "Username not found";
        final AuthenticationException e = new UsernameNotFoundException(msg);
        sut.onAuthenticationFailure(request, response, e);
        final LoginStatus status = mapper.readValue(response.getContentAsString(), LoginStatus.class);
        assertFalse(status.isSuccess());
        assertFalse(status.isLoggedIn());
        assertNull(status.getUsername());
        assertEquals(msg, status.getErrorMessage());
        assertEquals("login.error", status.getErrorId());
    }

    @Test
    void authenticationFailureReturnsLoginStatusWithErrorInfoOnAccountLocked() throws Exception {
        final MockHttpServletRequest request = request();
        final MockHttpServletResponse response = response();
        final String msg = "Account is locked.";
        sut.onAuthenticationFailure(request, response, new LockedException(msg));
        final LoginStatus status = mapper.readValue(response.getContentAsString(), LoginStatus.class);
        assertFalse(status.isSuccess());
        assertFalse(status.isLoggedIn());
        assertNull(status.getUsername());
        assertEquals(msg, status.getErrorMessage());
        assertEquals("login.locked", status.getErrorId());
    }

    @Test
    void authenticationFailureReturnsLoginStatusWithErrorInfoOnAccountDisabled() throws Exception {
        final MockHttpServletRequest request = request();
        final MockHttpServletResponse response = response();
        final String msg = "Account is disabled.";
        sut.onAuthenticationFailure(request, response, new DisabledException(msg));
        final LoginStatus status = mapper.readValue(response.getContentAsString(), LoginStatus.class);
        assertFalse(status.isSuccess());
        assertFalse(status.isLoggedIn());
        assertNull(status.getUsername());
        assertEquals(msg, status.getErrorMessage());
        assertEquals("login.disabled", status.getErrorId());
    }
}
