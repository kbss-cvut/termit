package cz.cvut.kbss.termit.util.oidc;

import cz.cvut.kbss.termit.security.model.UserRole;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Utils;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OidcGrantedAuthoritiesExtractorTest {

    private final Configuration.Security config = new Configuration.Security();

    @Test
    void convertMapsTopLevelClaimWithRolesToGrantedAuthorities() {
        config.setRoleClaim("roles");
        final List<String> roles = List.of(UserRole.ADMIN.getName(), UserRole.FULL_USER.getName());
        final Jwt token = Jwt.withTokenValue("abcdef12345")
                             .header("alg", "RS256")
                             .header("typ", "JWT")
                             .claim(config.getRoleClaim(), roles)
                             .issuer("http://localhost:8080/termit")
                             .subject("termit")
                             .expiresAt(Utils.timestamp().plusSeconds(300))
                             .build();

        final OidcGrantedAuthoritiesExtractor sut = new OidcGrantedAuthoritiesExtractor(config);
        final Collection<SimpleGrantedAuthority> result = sut.convert(token);
        assertNotNull(result);
        for (String r : roles) {
            assertThat(result, hasItem(new SimpleGrantedAuthority(r)));
        }
    }

    @Test
    void convertSupportsNestedRolesClaim() {
        config.setRoleClaim("realm_access.roles");
        final List<String> roles = List.of(UserRole.ADMIN.getName(), UserRole.FULL_USER.getName());
        final Jwt token = Jwt.withTokenValue("abcdef12345")
                             .header("alg", "RS256")
                             .header("typ", "JWT")
                             .claim("realm_access", Map.of("roles", roles))
                             .issuer("http://localhost:8080/termit")
                             .subject("termit")
                             .expiresAt(Utils.timestamp().plusSeconds(300))
                             .build();

        final OidcGrantedAuthoritiesExtractor sut = new OidcGrantedAuthoritiesExtractor(config);
        final Collection<SimpleGrantedAuthority> result = sut.convert(token);
        assertNotNull(result);
        for (String r : roles) {
            assertThat(result, hasItem(new SimpleGrantedAuthority(r)));
        }
    }

    @Test
    void convertThrowsIllegalArgumentExceptionWhenExpectedClaimPathIsNotTraversable() {
        config.setRoleClaim("realm_access.roles.list");
        final Jwt token = Jwt.withTokenValue("abcdef12345")
                             .header("alg", "RS256")
                             .header("typ", "JWT")
                             .claim("realm_access", Map.of("roles", 1235))
                             .issuer("http://localhost:8080/termit")
                             .subject("termit")
                             .expiresAt(Utils.timestamp().plusSeconds(300))
                             .build();

        final OidcGrantedAuthoritiesExtractor sut = new OidcGrantedAuthoritiesExtractor(config);
        assertThrows(IllegalArgumentException.class, () -> sut.convert(token));
    }

    @Test
    void convertThrowsIllegalArgumentExceptionWhenNestedRolesClaimIsNotList() {
        config.setRoleClaim("realm_access.roles.notlist");
        final Jwt token = Jwt.withTokenValue("abcdef12345")
                             .header("alg", "RS256")
                             .header("typ", "JWT")
                             .claim("realm_access", Map.of("roles", Map.of("notlist", UserRole.FULL_USER.getName())))
                             .issuer("http://localhost:8080/termit")
                             .subject("termit")
                             .expiresAt(Utils.timestamp().plusSeconds(300))
                             .build();

        final OidcGrantedAuthoritiesExtractor sut = new OidcGrantedAuthoritiesExtractor(config);
        assertThrows(IllegalArgumentException.class, () -> sut.convert(token));
    }
}
