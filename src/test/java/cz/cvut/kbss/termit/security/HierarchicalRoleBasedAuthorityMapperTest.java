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
