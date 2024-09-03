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
package cz.cvut.kbss.termit.config;

import cz.cvut.kbss.termit.security.AuthenticationSuccess;
import cz.cvut.kbss.termit.security.HierarchicalRoleBasedAuthorityMapper;
import cz.cvut.kbss.termit.security.JwtUtils;
import cz.cvut.kbss.termit.security.SecurityConstants;
import cz.cvut.kbss.termit.security.WebSocketJwtAuthorizationInterceptor;
import cz.cvut.kbss.termit.service.security.TermItUserDetailsService;
import cz.cvut.kbss.termit.util.oidc.OidcGrantedAuthoritiesExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.core.convert.converter.Converter;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.annotation.support.SimpAnnotationMethodMessageHandler;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Collection;

import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

@ConditionalOnProperty(prefix = "termit.security", name = "provider", havingValue = "oidc")
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class OAuth2SecurityConfig {

    private static final Logger LOG = LoggerFactory.getLogger(OAuth2SecurityConfig.class);

    private final AuthenticationSuccess authenticationSuccessHandler;

    private final cz.cvut.kbss.termit.util.Configuration config;

    @Autowired
    public OAuth2SecurityConfig(AuthenticationSuccess authenticationSuccessHandler,
                                cz.cvut.kbss.termit.util.Configuration config) {
        this.authenticationSuccessHandler = authenticationSuccessHandler;
        this.config = config;
    }

    @Bean
    protected SessionAuthenticationStrategy sessionAuthenticationStrategy() {
        return new RegisterSessionAuthenticationStrategy(new SessionRegistryImpl());
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        LOG.debug("Using OAuth2/OIDC security.");
        http.oauth2ResourceServer(
                    (auth) -> auth.jwt((jwt) -> jwt.jwtAuthenticationConverter(grantedAuthoritiesExtractor())))
            .authorizeHttpRequests((auth) -> auth.requestMatchers(antMatcher("/rest/query")).permitAll()
                                                 .requestMatchers(antMatcher("/**")).permitAll())
            .cors((auth) -> auth.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .logout((auth) -> auth.logoutUrl(SecurityConstants.LOGOUT_PATH)
                                  .logoutSuccessHandler(authenticationSuccessHandler));
        return http.build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        return SecurityConfig.createCorsConfiguration(config.getCors());
    }

    private Converter<Jwt, AbstractAuthenticationToken> grantedAuthoritiesExtractor() {
        return source -> {
            final Collection<SimpleGrantedAuthority> authorities = new OidcGrantedAuthoritiesExtractor(
                    config.getSecurity()).convert(source);
            return new JwtAuthenticationToken(source,
                                              new HierarchicalRoleBasedAuthorityMapper().mapAuthorities(authorities));
        };
    }

    /**
     * Part of {@link EnableWebSocketSecurity @EnableWebSocketSecurity} replacement
     *
     * @see WebSocketConfig
     */
    @Bean
    @Scope("prototype")
    public MessageMatcherDelegatingAuthorizationManager.Builder messageAuthorizationManagerBuilder(
            ApplicationContext context) {
        return MessageMatcherDelegatingAuthorizationManager.builder().simpDestPathMatcher(
                () -> (context.getBeanNamesForType(SimpAnnotationMethodMessageHandler.class).length > 0)
                        ? context.getBean(SimpAnnotationMethodMessageHandler.class).getPathMatcher()
                        : new AntPathMatcher());
    }

    /**
     * WebSocket endpoint authorization
     */
    @Bean
    public AuthorizationManager<Message<?>> messageAuthorizationManager(
            MessageMatcherDelegatingAuthorizationManager.Builder messages) {
        return messages.simpTypeMatchers(SimpMessageType.DISCONNECT).permitAll()
                       .anyMessage().authenticated().build();
    }

    @Bean
    public WebSocketJwtAuthorizationInterceptor webSocketJwtAuthorizationInterceptor(JwtUtils jwtUtils,
                                                                                     TermItUserDetailsService userDetailsService) {
        return new WebSocketJwtAuthorizationInterceptor(jwtUtils, userDetailsService);
    }
}
