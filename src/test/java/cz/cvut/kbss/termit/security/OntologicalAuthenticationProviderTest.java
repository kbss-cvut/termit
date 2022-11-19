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

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.event.LoginFailureEvent;
import cz.cvut.kbss.termit.event.LoginSuccessEvent;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.security.model.TermItUserDetails;
import cz.cvut.kbss.termit.service.security.TermItUserDetailsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("security")
@ExtendWith(MockitoExtension.class)
class OntologicalAuthenticationProviderTest {

    @Mock
    private TermItUserDetailsService userDetailsService;

    @Spy
    private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private OntologicalAuthenticationProvider sut;

    private UserAccount user;
    private String plainPassword;

    @BeforeEach
    void setUp() {
        this.user = Generator.generateUserAccountWithPassword();
        this.plainPassword = user.getPassword();
        user.setPassword(passwordEncoder.encode(plainPassword));
        SecurityContextHolder.setContext(new SecurityContextImpl());
        sut.setApplicationEventPublisher(eventPublisher);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.setContext(new SecurityContextImpl());
    }

    @Test
    void successfulAuthenticationSetsSecurityContext() {
        final Authentication auth = authentication(user.getUsername(), plainPassword);
        final SecurityContext context = SecurityContextHolder.getContext();
        assertNull(context.getAuthentication());
        when(userDetailsService.loadUserByUsername(user.getUsername())).thenReturn(new TermItUserDetails(user));

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
        when(userDetailsService.loadUserByUsername(anyString())).thenThrow(new UsernameNotFoundException("Unknown"));
        assertThrows(UsernameNotFoundException.class, () -> sut.authenticate(auth));
        final SecurityContext context = SecurityContextHolder.getContext();
        assertNull(context.getAuthentication());
    }

    @Test
    void authenticateThrowsBadCredentialsForInvalidPassword() {
        when(userDetailsService.loadUserByUsername(user.getUsername())).thenReturn(new TermItUserDetails(user));
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
        verify(userDetailsService, never()).loadUserByUsername("");
    }

    @Test
    void successfulLoginEmitsLoginSuccessEvent() {
        when(userDetailsService.loadUserByUsername(user.getUsername())).thenReturn(new TermItUserDetails(user));
        final Authentication auth = authentication(user.getUsername(), plainPassword);
        sut.authenticate(auth);
        final ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue(), instanceOf(LoginSuccessEvent.class));
        assertEquals(user, ((LoginSuccessEvent) captor.getValue()).getUser());
    }

    @Test
    void failedLoginEmitsLoginFailureEvent() {
        when(userDetailsService.loadUserByUsername(user.getUsername())).thenReturn(new TermItUserDetails(user));
        final Authentication auth = authentication(user.getUsername(), "unknownPassword");
        assertThrows(BadCredentialsException.class, () -> sut.authenticate(auth));
        final ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue(), instanceOf(LoginFailureEvent.class));
        assertEquals(user, ((LoginFailureEvent) captor.getValue()).getUser());
    }

    @Test
    void authenticateThrowsLockedExceptionForLockedUser() {
        user.lock();
        when(userDetailsService.loadUserByUsername(user.getUsername())).thenReturn(new TermItUserDetails(user));
        final Authentication auth = authentication(user.getUsername(), plainPassword);
        final LockedException ex = assertThrows(LockedException.class, () -> sut.authenticate(auth));
        assertEquals("Account of user " + user + " is locked.", ex.getMessage());
    }

    @Test
    void authenticationThrowsDisabledExceptionForDisabledUser() {
        user.disable();
        when(userDetailsService.loadUserByUsername(user.getUsername())).thenReturn(new TermItUserDetails(user));
        final Authentication auth = authentication(user.getUsername(), plainPassword);
        final DisabledException ex = assertThrows(DisabledException.class, () -> sut.authenticate(auth));
        assertEquals("Account of user " + user + " is disabled.", ex.getMessage());
    }
}
