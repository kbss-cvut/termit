package cz.cvut.kbss.termit.config;

import cz.cvut.kbss.termit.security.AuthenticationSuccess;
import cz.cvut.kbss.termit.security.HierarchicalRoleBasedAuthorityMapper;
import cz.cvut.kbss.termit.security.SecurityConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@ConditionalOnProperty(prefix = "keycloak", name = "enabled", havingValue = "true")
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class KeycloakSecurityConfig {

    private static final Logger LOG = LoggerFactory.getLogger(KeycloakSecurityConfig.class);

    private final AuthenticationSuccess authenticationSuccessHandler;

    private final cz.cvut.kbss.termit.util.Configuration config;

    @Autowired
    public KeycloakSecurityConfig(AuthenticationSuccess authenticationSuccessHandler,
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
        LOG.debug("Using Keycloak security.");
        http.oauth2Login(Customizer.withDefaults())
            .oauth2ResourceServer(
                    (auth) -> auth.jwt((jwt) -> jwt.jwtAuthenticationConverter(grantedAuthoritiesExtractor())))
            .authorizeHttpRequests((auth) -> auth.requestMatchers("/rest/query").permitAll()
                                                 .requestMatchers("/**").permitAll())
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
            final Collection<SimpleGrantedAuthority> authorities = new GrantedAuthoritiesExtractor().convert(
                    source);
            return new OAuth2AuthenticationToken(new DefaultOAuth2User(authorities, source.getClaims(), "name"),
                                                 authorities, null);
        };
    }

    static class GrantedAuthoritiesExtractor implements Converter<Jwt, Collection<SimpleGrantedAuthority>> {
        public Collection<SimpleGrantedAuthority> convert(Jwt jwt) {
            final List<SimpleGrantedAuthority> allAuths = (
                    (Map<String, Collection<?>>) jwt.getClaims().getOrDefault("realm_access", Collections.emptyMap())
            ).getOrDefault("roles", Collections.emptyList())
             .stream()
             .map(Object::toString)
             .map(SimpleGrantedAuthority::new).toList();
            return new HierarchicalRoleBasedAuthorityMapper().mapAuthorities(allAuths);
        }
    }
}
