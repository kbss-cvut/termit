package cz.cvut.kbss.termit.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import cz.cvut.kbss.termit.security.JwtAuthenticationFilter;
import cz.cvut.kbss.termit.security.JwtAuthorizationFilter;
import cz.cvut.kbss.termit.security.JwtUserDetailsValidator;
import cz.cvut.kbss.termit.security.JwtUtils;
import cz.cvut.kbss.termit.security.PatAuthenticationConverter;
import cz.cvut.kbss.termit.security.PatToUserDetailsConverter;
import cz.cvut.kbss.termit.security.SecurityConstants;
import cz.cvut.kbss.termit.service.business.PersonalAccessTokenService;
import cz.cvut.kbss.termit.util.Constants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.List;
import java.util.Map;

/**
 * Provides helper methods for configuring JWT authentication.
 */
@Configuration
public class JwtConfig {
    private final JwtUtils jwtUtils;
    private final ObjectMapper objectMapper;

    public JwtConfig(JwtUtils jwtUtils, ObjectMapper objectMapper) {
        this.jwtUtils = jwtUtils;
        this.objectMapper = objectMapper;
    }

    public OAuth2TokenValidator<Jwt> jwtValidator() {
        return new DelegatingOAuth2TokenValidator<>(List.of(
                // checks token expiration with default clockSkew 60s
                new JwtTimestampValidator(),
                // validates user details loaded into the token
                new JwtUserDetailsValidator()
        ));
    }

    /**
     * configures {@link DefaultJOSEObjectTypeVerifier} accepting {@link JOSEObjectType#JWT JWT}
     * and {@link Constants.MediaType#JWT_ACCESS_TOKEN JWT+AT} token types
     */
    public static void setJWSTypeVerifier(ConfigurableJWTProcessor<SecurityContext> configurer) {
        configurer.setJWSTypeVerifier(new DefaultJOSEObjectTypeVerifier<>(
                JOSEObjectType.JWT, new JOSEObjectType(Constants.MediaType.JWT_ACCESS_TOKEN), null));
    }

    /**
     * Constructs JWT decoder using TermIt signing key,
     * {@link SecurityConstants#JWT_DEFAULT_KEY_ALGORITHM default algorithm},
     * which accepts JWT keys with {@link #setJWSTypeVerifier(ConfigurableJWTProcessor) allowed types}.
     */
    public NimbusJwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(jwtUtils.getJwtSigningKey())
                               .macAlgorithm(MacAlgorithm.from(SecurityConstants.JWT_DEFAULT_KEY_ALGORITHM))
                               .jwtProcessorCustomizer(JwtConfig::setJWSTypeVerifier)
                               .build();
    }

    public JwtAuthorizationFilter jwtAuthorizationFilter(AuthenticationManager authenticationManager) {
        return new JwtAuthorizationFilter(authenticationManager, jwtUtils, objectMapper);
    }

    public JwtAuthenticationFilter jwtAuthenticationFilter(AuthenticationManager authenticationManager) {
        return new JwtAuthenticationFilter(authenticationManager, jwtUtils);
    }

    /**
     * Constructs {@link JwtAuthenticationProvider} using the specified {@link JwtDecoder}.
     * The provider is configured with authentication converter for TermIt roles format.
     * @see SecurityConstants#JWT_ROLE_DELIMITER
     * @see SecurityConstants#JWT_ROLE_CLAIM
     * @param jwtDecoder The JWT decoder to pass to {@link JwtAuthenticationProvider}
     * @return The configured {@link JwtAuthenticationProvider}
     */
    public JwtAuthenticationProvider jwtAuthenticationProvider(JwtDecoder jwtDecoder) {
        final JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimDelimiter(SecurityConstants.JWT_ROLE_DELIMITER);
        authoritiesConverter.setAuthoritiesClaimName(SecurityConstants.JWT_ROLE_CLAIM);
        authoritiesConverter.setAuthorityPrefix(""); // this removes default "SCOPE_" prefix
        // otherwise, all granted authorities would have this prefix
        // (like "SCOPE_ROLE_RESTRICTED_USER", we want just ROLE_...)
        final JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        final JwtAuthenticationProvider provider = new JwtAuthenticationProvider(jwtDecoder);
        provider.setJwtAuthenticationConverter(converter);
        return provider;
    }

    /**
     * Constructs {@link JwtAuthenticationProvider} for PAT authentication
     */
    public JwtAuthenticationProvider patAuthenticationProvider(PersonalAccessTokenService personalAccessTokenService) {
        final JwtAuthenticationProvider patAuthenticationProvider =
                jwtAuthenticationProvider(patDecoder(personalAccessTokenService));
        patAuthenticationProvider.setJwtAuthenticationConverter(new PatAuthenticationConverter());
        return patAuthenticationProvider;
    }

    /**
     * Replaces autoconfigured authentication manager with {@link ProviderManager}
     * using all active {@link AuthenticationProvider} beans.
     */
    @Bean
    public AuthenticationManager authenticationManager(List<AuthenticationProvider> authenticationProviders) {
        return new ProviderManager(authenticationProviders);
    }

    public JwtDecoder patDecoder(PersonalAccessTokenService personalAccessTokenService) {
        NimbusJwtDecoder decoder = jwtDecoder();
        decoder.setClaimSetConverter(MappedJwtClaimSetConverter.withDefaults(
                Map.of(JwtClaimNames.SUB, new PatToUserDetailsConverter(personalAccessTokenService))
        ));
        decoder.setJwtValidator(jwtValidator());
        return decoder;
    }
}
