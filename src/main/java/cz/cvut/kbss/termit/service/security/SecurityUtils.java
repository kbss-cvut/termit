/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If not, see
 * <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.service.security;

import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.security.model.AuthenticationToken;
import cz.cvut.kbss.termit.security.model.TermItUserDetails;
import cz.cvut.kbss.termit.security.model.UserRole;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.util.ConfigParam;
import org.keycloak.KeycloakPrincipal;
import org.keycloak.representations.AccessToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Handle user session-related functions.
 */
@Service
public class SecurityUtils {

    private final IdentifierResolver idResolver;

    @Autowired
    public SecurityUtils(IdentifierResolver idResolver) {
        this.idResolver = idResolver;
        // Ensures security context is propagated to additionally spun threads, e.g., used by @Async methods
        SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL);
    }

    /**
     * Gets the currently authenticated user.
     *
     * @return Current user
     */
    public UserAccount getCurrentUser() {
        final SecurityContext context = SecurityContextHolder.getContext();
        assert context != null && context.getAuthentication().isAuthenticated();
        if (context.getAuthentication().getPrincipal() instanceof KeycloakPrincipal) {
            return resolveAccountFromKeycloakPrincipal(context);
        } else {
            assert context.getAuthentication() instanceof AuthenticationToken;
            return ((TermItUserDetails) context.getAuthentication().getDetails()).getUser();
        }
    }

    private UserAccount resolveAccountFromKeycloakPrincipal(SecurityContext context) {
        final KeycloakPrincipal<?> principal = (KeycloakPrincipal<?>) context.getAuthentication().getPrincipal();
        final AccessToken keycloakToken = principal.getKeycloakSecurityContext().getToken();
        final UserAccount account = new UserAccount();
        account.setFirstName(keycloakToken.getGivenName());
        account.setLastName(keycloakToken.getFamilyName());
        account.setUsername(keycloakToken.getPreferredUsername());
        context.getAuthentication().getAuthorities().stream().map(ga -> UserRole.fromRoleName(ga.getAuthority()))
               .filter(r -> !r.getType().isEmpty()).forEach(r -> account.addType(r.getType()));
        account.setUri(idResolver.generateIdentifier(ConfigParam.NAMESPACE_USER, keycloakToken.getSubject()));
        return account;
    }

    /**
     * Checks if a user is currently authenticated, or if the current thread is processing a request from an anonymous
     * user.
     *
     * @return Whether a user is authenticated
     */
    public static boolean authenticated() {
        final SecurityContext context = SecurityContextHolder.getContext();
        return context.getAuthentication() != null && context.getAuthentication().isAuthenticated();
    }

    /**
     * @see #authenticated()
     */
    public boolean isAuthenticated() {
        return authenticated();
    }
}
