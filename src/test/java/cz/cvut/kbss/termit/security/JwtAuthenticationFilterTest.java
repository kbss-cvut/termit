/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.security;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.environment.config.TestConfig;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.security.model.AuthenticationToken;
import cz.cvut.kbss.termit.security.model.TermItUserDetails;
import cz.cvut.kbss.termit.util.Configuration;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.servlet.FilterChain;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("security")
@ExtendWith({SpringExtension.class, MockitoExtension.class})
@ContextConfiguration(classes = {TestConfig.class}, initializers = {ConfigDataApplicationContextInitializer.class})
class JwtAuthenticationFilterTest {

    @Autowired
    private Configuration config;

    private MockHttpServletRequest mockRequest;

    private MockHttpServletResponse mockResponse;

    private UserAccount user;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter sut;

    @BeforeEach
    void setUp() {
        this.user = Generator.generateUserAccount();
        this.mockRequest = new MockHttpServletRequest();
        this.mockResponse = new MockHttpServletResponse();
        this.sut = new JwtAuthenticationFilter(mock(AuthenticationManager.class), new JwtUtils(config));
    }

    @Test
    void successfulAuthenticationAddsJWTToResponse() throws Exception {
        final AuthenticationToken token = new AuthenticationToken(Collections.emptySet(), new TermItUserDetails(user));
        sut.successfulAuthentication(mockRequest, mockResponse, filterChain, token);
        assertTrue(mockResponse.containsHeader(HttpHeaders.AUTHORIZATION));
        final String value = mockResponse.getHeader(HttpHeaders.AUTHORIZATION);
        assertNotNull(value);
        assertTrue(value.startsWith(SecurityConstants.JWT_TOKEN_PREFIX));
        final String jwtToken = value.substring(SecurityConstants.JWT_TOKEN_PREFIX.length());
        final Jws<Claims> jwt = Jwts.parser().setSigningKey(config.getJwt().getSecretKey())
                .parseClaimsJws(jwtToken);
        assertFalse(jwt.getBody().isEmpty());
    }
}
