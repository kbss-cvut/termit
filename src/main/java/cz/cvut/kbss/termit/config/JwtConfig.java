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
import cz.cvut.kbss.termit.security.SecurityConstants;
import cz.cvut.kbss.termit.util.Constants;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.List;

@Configuration
public class JwtConfig {
    private final JwtUtils jwtUtils;
    private final ObjectMapper objectMapper;

    public JwtConfig(JwtUtils jwtUtils, ObjectMapper objectMapper) {
        this.jwtUtils = jwtUtils;
        this.objectMapper = objectMapper;
    }

    public static OAuth2TokenValidator<Jwt> jwtValidator() {
        return new DelegatingOAuth2TokenValidator<>(List.of(
                // checks token expiration with default clockSkew 60s
                new JwtTimestampValidator(),
                // validates user details loaded into the token
                new JwtUserDetailsValidator()
        ));
    }

    /**
     * sets {@link DefaultJOSEObjectTypeVerifier} accepting {@code JWT} and {@code JWT+AT} token types
     */
    public static void setJWSTypeVerifier(ConfigurableJWTProcessor<SecurityContext> configurer) {
        configurer.setJWSTypeVerifier(new DefaultJOSEObjectTypeVerifier<>(
                JOSEObjectType.JWT, new JOSEObjectType(Constants.MediaType.JWT_ACCESS_TOKEN), null));
    }

    public NimbusJwtDecoder jwtDecoder() {
        final NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtUtils.getJwtSigningKey())
                                                         .macAlgorithm(MacAlgorithm.from(SecurityConstants.JWT_DEFAULT_KEY_ALGORITHM))
                                                         .jwtProcessorCustomizer(JwtConfig::setJWSTypeVerifier)
                                                         .build();
        return decoder;
    }

    public JwtAuthorizationFilter jwtAuthorizationFilter(AuthenticationManager authenticationManager, JwtDecoder jwtDecoder) {
        return new JwtAuthorizationFilter(authenticationManager, jwtUtils, objectMapper, jwtDecoder);
    }

    public JwtAuthenticationFilter jwtAuthenticationFilter(AuthenticationManager authenticationManager) {
        return new JwtAuthenticationFilter(authenticationManager, jwtUtils);
    }

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
}
