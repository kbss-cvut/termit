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
package cz.cvut.kbss.termit.util.oidc;

import cz.cvut.kbss.termit.util.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Converts roles claim in an OIDC access token to granted authorities.
 */
public class OidcGrantedAuthoritiesExtractor implements Converter<Jwt, Collection<SimpleGrantedAuthority>> {

    private final Configuration.Security config;

    public OidcGrantedAuthoritiesExtractor(Configuration.Security config) {
        this.config = config;
    }

    @Override
    public Collection<SimpleGrantedAuthority> convert(Jwt source) {
        final String rolesClaim = config.getRoleClaim();
        final String[] parts = rolesClaim.split("\\.");
        assert parts.length > 0;
        final List<String> roles;
        if (parts.length == 1) {
            roles = source.getClaimAsStringList(rolesClaim);
        } else {
            Map<String, Object> map = source.getClaimAsMap(parts[0]);
            for (int i = 1; i < parts.length - 1; i++) {
                if (map.containsKey(parts[i]) && !(map.get(parts[i]) instanceof Map)) {
                    throw new IllegalArgumentException(
                            "Access token does not contain roles under the expected claim '" + rolesClaim + "'.");
                }
                map = (Map<String, Object>) map.getOrDefault(parts[i], Collections.emptyMap());
            }
            if (map.containsKey(parts[parts.length - 1]) && !(map.get(parts[parts.length - 1]) instanceof List)) {
                throw new IllegalArgumentException("Roles claim does not contain a list.");
            }
            roles = (List<String>) map.getOrDefault(parts[parts.length - 1], Collections.emptyList());
        }
        return roles.stream().map(SimpleGrantedAuthority::new).toList();
    }
}
