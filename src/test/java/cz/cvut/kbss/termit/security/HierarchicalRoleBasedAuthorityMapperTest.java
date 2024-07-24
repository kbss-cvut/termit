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
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;

class HierarchicalRoleBasedAuthorityMapperTest {

    private final HierarchicalRoleBasedAuthorityMapper sut = new HierarchicalRoleBasedAuthorityMapper();

    @Test
    void mapAuthoritiesAddsLowerLevelRoleAuthoritiesToResult() {
        final Collection<SimpleGrantedAuthority> result = sut.mapAuthorities(
                Collections.singleton(new SimpleGrantedAuthority(UserRole.FULL_USER.getName())));
        UserRole.FULL_USER.getGranted()
                          .forEach(r -> assertThat(result, hasItem(new SimpleGrantedAuthority(r.getName()))));
    }
}
