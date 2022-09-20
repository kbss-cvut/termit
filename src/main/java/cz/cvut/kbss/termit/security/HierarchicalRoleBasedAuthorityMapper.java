package cz.cvut.kbss.termit.security;

import cz.cvut.kbss.termit.security.model.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Maps TermIt roles to Spring Security authorities.
 * <p>
 * Because TermIt roles are hierarchical, this mapper ensures that higher-level roles include authorities of the
 * lower-level roles (e.g., full user has authorities of restricted user).
 */
public class HierarchicalRoleBasedAuthorityMapper implements GrantedAuthoritiesMapper {

    @Override
    public Collection<SimpleGrantedAuthority> mapAuthorities(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream().map(a -> {
                              final UserRole role = UserRole.fromRoleName(a.getAuthority());
                              return role.getGranted();
                          }).flatMap(roles -> roles.stream().map(r -> new SimpleGrantedAuthority(r.getName())))
                          .collect(Collectors.toSet());
    }
}
