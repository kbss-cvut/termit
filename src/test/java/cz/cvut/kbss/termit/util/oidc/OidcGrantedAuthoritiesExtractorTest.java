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
