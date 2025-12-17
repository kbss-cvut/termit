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
package cz.cvut.kbss.termit.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import cz.cvut.kbss.termit.security.AuthenticationSuccess;
import cz.cvut.kbss.termit.security.JwtAuthenticationFilter;
import cz.cvut.kbss.termit.security.JwtAuthorizationFilter;
import cz.cvut.kbss.termit.security.JwtUtils;
import cz.cvut.kbss.termit.security.SecurityConstants;
import cz.cvut.kbss.termit.security.TermitJwtDecoder;
import cz.cvut.kbss.termit.service.security.TermItUserDetailsService;
import cz.cvut.kbss.termit.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.Collections;

@ConditionalOnProperty(prefix = "termit.security", name = "provider", havingValue = "internal", matchIfMissing = true)
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger LOG = LoggerFactory.getLogger(SecurityConfig.class);

    private final AuthenticationProvider authenticationProvider;

    private final AuthenticationSuccess authenticationSuccessHandler;

    private final AuthenticationFailureHandler authenticationFailureHandler;

    private final JwtUtils jwtUtils;

    private final TermItUserDetailsService userDetailsService;

    private final ObjectMapper objectMapper;

    private final cz.cvut.kbss.termit.util.Configuration config;

    @Autowired
    public SecurityConfig(AuthenticationProvider authenticationProvider,
                          AuthenticationSuccess authenticationSuccessHandler,
                          AuthenticationFailureHandler authenticationFailureHandler,
                          JwtUtils jwtUtils, TermItUserDetailsService userDetailsService,
                          ObjectMapper objectMapper, cz.cvut.kbss.termit.util.Configuration config) {
        this.authenticationProvider = authenticationProvider;
        this.authenticationSuccessHandler = authenticationSuccessHandler;
        this.authenticationFailureHandler = authenticationFailureHandler;
        this.jwtUtils = jwtUtils;
        this.userDetailsService = userDetailsService;
        this.objectMapper = objectMapper;
        this.config = config;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, TermitJwtDecoder jwtDecoder) throws Exception {
        LOG.debug("Using internal security mechanisms.");
        final AuthenticationManager authManager = buildAuthenticationManager(http);
        final PathPatternRequestMatcher.Builder matcher = PathPatternRequestMatcher.withDefaults();
        http.authorizeHttpRequests((auth) -> auth.requestMatchers(matcher.matcher("/**")).permitAll())
            .cors((auth) -> auth.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .exceptionHandling(ehc -> ehc.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .logout((auth) -> auth.logoutUrl(SecurityConstants.LOGOUT_PATH)
                                  .logoutSuccessHandler(authenticationSuccessHandler))
            .authenticationManager(authManager)
            .addFilter(authenticationFilter(authManager))
            .addFilter(new JwtAuthorizationFilter(authManager, jwtUtils, objectMapper, jwtDecoder));
        return http.build();
    }

    private AuthenticationManager buildAuthenticationManager(HttpSecurity http) throws Exception {
        final AuthenticationManagerBuilder ab = http.getSharedObject(AuthenticationManagerBuilder.class);
        ab.authenticationProvider(authenticationProvider);
        return ab.build();
    }

    private JwtAuthenticationFilter authenticationFilter(AuthenticationManager authenticationManager) {
        final JwtAuthenticationFilter authenticationFilter = new JwtAuthenticationFilter(authenticationManager,
                                                                                         jwtUtils);
        authenticationFilter.setFilterProcessesUrl(SecurityConstants.LOGIN_PATH);
        authenticationFilter.setAuthenticationSuccessHandler(authenticationSuccessHandler);
        authenticationFilter.setAuthenticationFailureHandler(authenticationFailureHandler);
        return authenticationFilter;
    }

    /**
     * @see cz.cvut.kbss.termit.security.WebSocketJwtAuthorizationInterceptor
     */
    @Bean
    public JwtAuthenticationProvider jwtAuthenticationProvider(JwtDecoder jwtDecoder) {
        final JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix(""); // this removes default "SCOPE_" prefix
        // otherwise, all granted authorities would have this prefix
        // (like "SCOPE_ROLE_RESTRICTED_USER", we want just ROLE_...)
        final JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        final JwtAuthenticationProvider provider = new JwtAuthenticationProvider(jwtDecoder);
        provider.setJwtAuthenticationConverter(converter);
        return provider;
    }

    private CorsConfigurationSource corsConfigurationSource() {
        return createCorsConfiguration(config.getCors());
    }

    protected static CorsConfigurationSource createCorsConfiguration(
            cz.cvut.kbss.termit.util.Configuration.Cors corsConfig) {
        // Since we are using cookie-based sessions, we have to specify the URL of the clients (CORS allowed origins)
        final CorsConfiguration corsConfiguration = new CorsConfiguration().applyPermitDefaultValues();
        corsConfiguration.setAllowedMethods(Collections.singletonList("*"));
        corsConfiguration.setAllowedOrigins(Arrays.asList(corsConfig.getAllowedOrigins().split(",")));
        if (corsConfig.getAllowedOriginPatterns() != null) {
            corsConfiguration.setAllowedOriginPatterns(Arrays.asList(corsConfig.getAllowedOriginPatterns().split(",")));
        }
        corsConfiguration.addExposedHeader(HttpHeaders.AUTHORIZATION);
        corsConfiguration.addExposedHeader(HttpHeaders.LOCATION);
        corsConfiguration.addExposedHeader(HttpHeaders.CONTENT_DISPOSITION);
        corsConfiguration.addExposedHeader(Constants.X_TOTAL_COUNT_HEADER);
        corsConfiguration.setAllowCredentials(true);
        final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfiguration);
        return source;
    }

    @Bean
    public TermitJwtDecoder jwtDecoder() {
        return new TermitJwtDecoder(jwtUtils, userDetailsService);
    }
}
