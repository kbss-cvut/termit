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

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.event.LoginAttemptsThresholdExceeded;
import cz.cvut.kbss.termit.event.LoginFailureEvent;
import cz.cvut.kbss.termit.event.LoginSuccessEvent;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.security.SecurityConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {RuntimeBasedLoginTrackerTest.Config.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class RuntimeBasedLoginTrackerTest {

    @Configuration
    public static class Config {
        @Bean
        public LoginTracker loginTracker() {
            return new RuntimeBasedLoginTracker();
        }

        @Bean
        public LoginListener loginListener() {
            return spy(new LoginListener());
        }
    }

    @Autowired
    private LoginTracker loginTracker;

    @Autowired
    private LoginListener listener;

    private UserAccount user;

    @BeforeEach
    void setUp() {
        this.user = Generator.generateUserAccount();
    }

    @Test
    void emitsThresholdExceededEventWhenMaximumLoginCountIsExceeded() {
        for (int i = 0; i < SecurityConstants.MAX_LOGIN_ATTEMPTS; i++) {
            assertNull(listener.user);
            loginTracker.onLoginFailure(new LoginFailureEvent(user));
        }
        loginTracker.onLoginFailure(new LoginFailureEvent(user));
        assertNotNull(listener.user);
        assertEquals(user, listener.user);
    }

    @Test
    void doesNotReemitThresholdExceededWhenAdditionalLoginAttemptsAreMade() {
        for (int i = 0; i < SecurityConstants.MAX_LOGIN_ATTEMPTS * 2; i++) {
            loginTracker.onLoginFailure(new LoginFailureEvent(user));
        }
        verify(listener, times(1)).onEvent(ArgumentMatchers.any());
    }

    @Test
    void successfulLoginResetsCounter() {
        for (int i = 0; i < SecurityConstants.MAX_LOGIN_ATTEMPTS - 1; i++) {
            loginTracker.onLoginFailure(new LoginFailureEvent(user));
        }
        loginTracker.onLoginSuccess(new LoginSuccessEvent(user));
        for (int i = 0; i < SecurityConstants.MAX_LOGIN_ATTEMPTS; i++) {
            loginTracker.onLoginFailure(new LoginFailureEvent(user));
        }
        verify(listener, never()).onEvent(ArgumentMatchers.any());
    }

    public static class LoginListener {

        private UserAccount user;

        @EventListener
        public void onEvent(LoginAttemptsThresholdExceeded event) {
            this.user = event.getUser();
        }
    }
}
