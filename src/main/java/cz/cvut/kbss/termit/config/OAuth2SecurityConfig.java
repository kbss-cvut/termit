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
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
            final Collection<SimpleGrantedAuthority> authorities = new GrantedAuthoritiesExtractor().convert(source);
            return new JwtAuthenticationToken(source, authorities);
        };
    }

    private class GrantedAuthoritiesExtractor implements Converter<Jwt, Collection<SimpleGrantedAuthority>> {
        public Collection<SimpleGrantedAuthority> convert(Jwt jwt) {
            final List<SimpleGrantedAuthority> allAuths = (
                    (Map<String, Collection<?>>) jwt.getClaims().getOrDefault(
                            OAuth2SecurityConfig.this.config.getSecurity().getRoleClaim(), Collections.emptyMap())
            ).getOrDefault("roles", Collections.emptyList())
             .stream()
             .map(Object::toString)
             .map(SimpleGrantedAuthority::new).toList();
            return new HierarchicalRoleBasedAuthorityMapper().mapAuthorities(allAuths);
        }
    }
}
