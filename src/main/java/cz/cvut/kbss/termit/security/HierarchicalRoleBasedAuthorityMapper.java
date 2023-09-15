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
