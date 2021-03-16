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
import cz.cvut.kbss.termit.environment.config.TestSecurityConfig;
import cz.cvut.kbss.termit.event.LoginFailureEvent;
import cz.cvut.kbss.termit.event.LoginSuccessEvent;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.persistence.dao.UserAccountDao;
import cz.cvut.kbss.termit.security.model.TermItUserDetails;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

@Tag("security")
@ContextConfiguration(classes = {TestSecurityConfig.class, OntologicalAuthenticationProviderTest.TestConfiguration.class})
class OntologicalAuthenticationProviderTest extends BaseServiceTestRunner {

    @Autowired
    private AuthenticationProvider sut;

    @Autowired
    private UserAccountDao userAccountDao;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private Listener listener;

    private UserAccount user;
    private String plainPassword;

    @BeforeEach
    void setUp() {
        this.user = Generator.generateUserAccountWithPassword();
        this.plainPassword = user.getPassword();
        user.setPassword(passwordEncoder.encode(plainPassword));
        transactional(() -> userAccountDao.persist(user));
        SecurityContextHolder.setContext(new SecurityContextImpl());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.setContext(new SecurityContextImpl());
        Mockito.reset(listener);
    }

    @Test
    void successfulAuthenticationSetsSecurityContext() {
        final Authentication auth = authentication(user.getUsername(), plainPassword);
        final SecurityContext context = SecurityContextHolder.getContext();
        assertNull(context.getAuthentication());
        final Authentication result = sut.authenticate(auth);
        assertNotNull(SecurityContextHolder.getContext());
        final TermItUserDetails details =
                (TermItUserDetails) SecurityContextHolder.getContext().getAuthentication().getDetails();
        assertEquals(user.getUsername(), details.getUsername());
        assertTrue(result.isAuthenticated());
    }

    private static Authentication authentication(String username, String password) {
        return new UsernamePasswordAuthenticationToken(username, password);
    }

    @Test
    void authenticateThrowsUserNotFoundExceptionForUnknownUsername() {
        final Authentication auth = authentication("unknownUsername", user.getPassword());
        assertThrows(UsernameNotFoundException.class, () -> sut.authenticate(auth));
        final SecurityContext context = SecurityContextHolder.getContext();
        assertNull(context.getAuthentication());
    }

    @Test
    void authenticateThrowsBadCredentialsForInvalidPassword() {
        final Authentication auth = authentication(user.getUsername(), "unknownPassword");
        assertThrows(BadCredentialsException.class, () -> sut.authenticate(auth));
        final SecurityContext context = SecurityContextHolder.getContext();
        assertNull(context.getAuthentication());
    }

    @Test
    void supportsUsernameAndPasswordAuthentication() {
        assertTrue(sut.supports(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void authenticateThrowsAuthenticationExceptionForEmptyUsername() {
        final Authentication auth = authentication("", "");
        final UsernameNotFoundException ex = assertThrows(UsernameNotFoundException.class,
                () -> sut.authenticate(auth));
        assertThat(ex.getMessage(), containsString("Username cannot be empty."));
    }

    @Test
    void successfulLoginEmitsLoginSuccessEvent() {
        final Authentication auth = authentication(user.getUsername(), plainPassword);
        sut.authenticate(auth);
        verify(listener).onSuccess(any());
        assertEquals(user, listener.user);
    }

    @Test
    void failedLoginEmitsLoginFailureEvent() {
        final Authentication auth = authentication(user.getUsername(), "unknownPassword");
        assertThrows(BadCredentialsException.class, () -> sut.authenticate(auth));
        verify(listener).onFailure(any());
        assertEquals(user, listener.user);
    }

    @Test
    void authenticateThrowsLockedExceptionForLockedUser() {
        user.lock();
        transactional(() -> userAccountDao.update(user));
        final Authentication auth = authentication(user.getUsername(), plainPassword);
        final LockedException ex = assertThrows(LockedException.class, () -> sut.authenticate(auth));
        assertEquals("Account of user " + user + " is locked.", ex.getMessage());
    }

    @Test
    void authenticationThrowsDisabledExceptionForDisabledUser() {
        user.disable();
        transactional(() -> userAccountDao.update(user));
        final Authentication auth = authentication(user.getUsername(), plainPassword);
        final DisabledException ex = assertThrows(DisabledException.class, () -> sut.authenticate(auth));
        assertEquals("Account of user " + user + " is disabled.", ex.getMessage());
    }

    @org.springframework.boot.test.context.TestConfiguration
    @ComponentScan(basePackages = "cz.cvut.kbss.termit.security")
    public static class TestConfiguration {
        @Bean
        public Listener listener() {
            return spy(new Listener());
        }

    }

    public static class Listener {

        private UserAccount user;

        @EventListener
        public void onSuccess(LoginSuccessEvent event) {
            this.user = event.getUser();
        }

        @EventListener
        public void onFailure(LoginFailureEvent event) {
            this.user = event.getUser();
        }
    }
}
