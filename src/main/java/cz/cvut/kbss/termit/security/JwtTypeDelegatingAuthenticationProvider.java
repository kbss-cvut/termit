package cz.cvut.kbss.termit.security;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jwt.JWTParser;
import cz.cvut.kbss.termit.util.Constants;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;

import java.text.ParseException;
import java.util.Optional;

/**
 * Delegates authentication to {@link #patJwtAuthenticationProvider}
 * if the JWT token has {@link Constants.MediaType#JWT_ACCESS_TOKEN} type,
 * otherwise delegates to {@link #defaultProvider}.
 */
public class JwtTypeDelegatingAuthenticationProvider implements AuthenticationProvider {
    private final AuthenticationProvider defaultProvider;
    private final JwtAuthenticationProvider patJwtAuthenticationProvider;

    public JwtTypeDelegatingAuthenticationProvider(AuthenticationProvider defaultProvider,
                                                   JwtAuthenticationProvider patJwtAuthenticationProvider) {
        this.defaultProvider = defaultProvider;
        this.patJwtAuthenticationProvider = patJwtAuthenticationProvider;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        final boolean isPat = resolveJwtType(authentication)
                .map(JOSEObjectType::getType)
                .map(Constants.MediaType.JWT_ACCESS_TOKEN::equalsIgnoreCase)
                .orElse(false);
        if (isPat) {
            return patJwtAuthenticationProvider.authenticate(authentication);
        } else {
            return defaultProvider.authenticate(authentication);
        }
    }

    private Optional<JOSEObjectType> resolveJwtType(Authentication token) {
        try {
            if (token instanceof BearerTokenAuthenticationToken bearerToken) {
                return Optional.ofNullable(JWTParser.parse(bearerToken.getToken()).getHeader().getType());
            }
        } catch (ParseException e) {
            // ignore
        }
        return Optional.empty();
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return BearerTokenAuthenticationToken.class.isAssignableFrom(authentication) || defaultProvider.supports(authentication);
    }
}
