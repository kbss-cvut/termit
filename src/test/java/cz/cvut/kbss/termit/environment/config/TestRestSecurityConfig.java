/**
 * TermIt
 * Copyright (C) 2019 Czech Technical University in Prague
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
package cz.cvut.kbss.termit.environment.config;

import cz.cvut.kbss.termit.security.AuthenticationFailure;
import cz.cvut.kbss.termit.security.AuthenticationSuccess;
import cz.cvut.kbss.termit.security.JwtUtils;
import cz.cvut.kbss.termit.service.security.SecurityUtils;
import cz.cvut.kbss.termit.service.security.TermItUserDetailsService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.AuthenticationProvider;

import static org.mockito.Mockito.mock;

/**
 * This configuration class is necessary when testing security of REST controllers (e.g., {@link
 * cz.cvut.kbss.termit.rest.UserController}).
 */
@TestConfiguration
public class TestRestSecurityConfig {

    @Bean
    public JwtUtils jwtUtils(cz.cvut.kbss.termit.util.Configuration configuration) {
        return new JwtUtils(configuration);
    }

    @Bean
    public SecurityUtils securityUtils() {
        return mock(SecurityUtils.class);
    }

    @Bean
    public AuthenticationSuccess authenticationSuccess() {
        return mock(AuthenticationSuccess.class);
    }

    @Bean
    public AuthenticationFailure authenticationFailure() {
        return mock(AuthenticationFailure.class);
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        return mock(AuthenticationProvider.class);
    }

    @Bean
    public TermItUserDetailsService userDetailsService() {
        return mock(TermItUserDetailsService.class);
    }
}
