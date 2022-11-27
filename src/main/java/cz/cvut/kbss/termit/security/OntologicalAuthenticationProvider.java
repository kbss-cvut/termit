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

import cz.cvut.kbss.termit.event.LoginFailureEvent;
import cz.cvut.kbss.termit.event.LoginSuccessEvent;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.security.model.AuthenticationToken;
import cz.cvut.kbss.termit.security.model.TermItUserDetails;
import cz.cvut.kbss.termit.service.security.SecurityUtils;
import cz.cvut.kbss.termit.service.security.TermItUserDetailsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class OntologicalAuthenticationProvider implements AuthenticationProvider, ApplicationEventPublisherAware {

    private static final Logger LOG = LoggerFactory.getLogger(OntologicalAuthenticationProvider.class);

    private final TermItUserDetailsService userDetailsService;

    private final PasswordEncoder passwordEncoder;

    private ApplicationEventPublisher eventPublisher;

    @Autowired
    public OntologicalAuthenticationProvider(TermItUserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        final String username = authentication.getPrincipal().toString();
        verifyUsernameNotEmpty(username);
        LOG.debug("Authenticating user {}", username);

        final TermItUserDetails userDetails = userDetailsService.loadUserByUsername(username);
        SecurityUtils.verifyAccountStatus(userDetails.getUser());
        final String password = (String) authentication.getCredentials();
        if (!passwordEncoder.matches(password, userDetails.getPassword())) {
            onLoginFailure(userDetails.getUser());
            throw new BadCredentialsException("Provided credentials don't match.");
        }
        onLoginSuccess(userDetails.getUser());
        return SecurityUtils.setCurrentUser(userDetails);
    }

    private static void verifyUsernameNotEmpty(String username) {
        if (username.isEmpty()) {
            throw new UsernameNotFoundException("Username cannot be empty.");
        }
    }

    private void onLoginFailure(UserAccount user) {
        user.erasePassword();
        eventPublisher.publishEvent(new LoginFailureEvent(user));
    }

    private void onLoginSuccess(UserAccount user) {
        eventPublisher.publishEvent(new LoginSuccessEvent(user));
    }

    @Override
    public boolean supports(Class<?> aClass) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(aClass) ||
                AuthenticationToken.class.isAssignableFrom(aClass);
    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }
}
