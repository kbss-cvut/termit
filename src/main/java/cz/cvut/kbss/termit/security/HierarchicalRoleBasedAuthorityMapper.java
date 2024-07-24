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
package cz.cvut.kbss.termit.security;

import cz.cvut.kbss.termit.security.model.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;

import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Maps TermIt roles to Spring Security authorities.
 * <p>
 * Because TermIt roles are hierarchical, this mapper ensures that higher-level roles include authorities of the
 * lower-level roles (e.g., full user has authorities of restricted user).
 */
public class HierarchicalRoleBasedAuthorityMapper implements GrantedAuthoritiesMapper {

    @Override
    public Collection<SimpleGrantedAuthority> mapAuthorities(Collection<? extends GrantedAuthority> authorities) {
        return resolveUserRolesFromAuthorities(authorities)
                .map(UserRole::getGranted)
                .flatMap(roles -> roles.stream().map(r -> new SimpleGrantedAuthority(r.getName())))
                .collect(Collectors.toSet());
    }

    public static Stream<UserRole> resolveUserRolesFromAuthorities(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream().filter(a -> UserRole.doesRoleExist(a.getAuthority()))
                          .map(a -> UserRole.fromRoleName(a.getAuthority()));
    }
}
