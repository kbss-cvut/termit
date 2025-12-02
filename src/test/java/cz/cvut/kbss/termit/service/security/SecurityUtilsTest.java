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
package cz.cvut.kbss.termit.service.security;

import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.ValidationException;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.security.model.TermItUserDetails;
import cz.cvut.kbss.termit.security.model.UserRole;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Utils;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@Execution(ExecutionMode.SAME_THREAD)
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unused")
class SecurityUtilsTest {

    @Mock
    private UserDetailsService userDetailsService;

    @Spy
    private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Spy
    private Configuration config = new Configuration();

    @Spy
    private IdentifierResolver idResolver = new IdentifierResolver(config);

    @InjectMocks
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
    void updateCurrentUserReplacesUserInCurrentSecurityContext() {
        Environment.setCurrentUser(user);
        final UserAccount update = new UserAccount();
        update.setUri(Generator.generateUri());
        update.setFirstName("updatedFirstName");
        update.setLastName("updatedLastName");
        update.setPassword(user.getPassword());
        update.setUsername(user.getUsername());
        when(userDetailsService.loadUserByUsername(user.getUsername())).thenReturn(new TermItUserDetails(update));
        sut.updateCurrentUser();

        final UserAccount currentUser = sut.getCurrentUser();
        assertEquals(update, currentUser);
    }

    @Test
    void verifyCurrentUserPasswordThrowsIllegalArgumentWhenPasswordDoesNotMatch() {
        Environment.setCurrentUser(user);
        final String password = "differentPassword";
        final ValidationException ex = assertThrows(ValidationException.class,
                                                    () -> sut.verifyCurrentUserPassword(password));
        assertThat(ex.getMessage(), containsString("does not match"));
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
    void isAuthenticatedReturnsFalseForAnonymousRequest() {
        final AnonymousAuthenticationToken token = new AnonymousAuthenticationToken("anonymousUser", "anonymousUser",
                                                                                    Collections.singleton(
                                                                                            new SimpleGrantedAuthority(
                                                                                                    "ROLE_ANONYMOUS")));
        token.setDetails(new WebAuthenticationDetails("0.0.0.0", null));
        token.setAuthenticated(true);
        SecurityContextHolder.setContext(new SecurityContextImpl(token));
        assertFalse(sut.isAuthenticated());
    }

    @Test
    void isAuthenticatedWorksInStaticVersion() {
        Environment.setCurrentUser(user);
        assertTrue(SecurityUtils.authenticated());
    }

    @Test
    void getCurrentUserSupportsOidcJwtAuthenticationTokens() {
        config.getNamespace().setUser(Vocabulary.s_c_uzivatel_termitu);
        final UserAccount user = Generator.generateUserAccount();
        final String subject = UUID.randomUUID().toString();
        user.setUri(idResolver.generateIdentifier(config.getNamespace().getUser(), subject));
        final Jwt jwt = Jwt.withTokenValue("12345").subject(subject)
                           .claim("given_name", user.getFirstName())
                           .claim("family_name", user.getLastName())
                           .claim("preferred_username", user.getUsername())
                           .issuedAt(Utils.timestamp())
                           .expiresAt(Utils.timestamp().plusSeconds(30))
                           .header("alg", "RS256").build();
        SecurityContextHolder.setContext(
                new SecurityContextImpl(new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(
                        UserRole.FULL_USER.getName())))));

        final UserAccount result = sut.getCurrentUser();
        assertEquals(user, result);
    }
}
