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

import cz.cvut.kbss.termit.exception.ValidationException;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.security.model.AuthenticationToken;
import cz.cvut.kbss.termit.security.model.TermItUserDetails;
import cz.cvut.kbss.termit.security.model.UserRole;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.util.ConfigParam;
import org.keycloak.KeycloakPrincipal;
import org.keycloak.representations.AccessToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Handle user session-related functions.
 */
@Service
public class SecurityUtils {

    private final PasswordEncoder passwordEncoder;
    private final IdentifierResolver idResolver;

    @Autowired
    public SecurityUtils(PasswordEncoder passwordEncoder, IdentifierResolver idResolver) {
        this.passwordEncoder = passwordEncoder;
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
        final KeycloakPrincipal<?> principal = (KeycloakPrincipal<?>) context.getAuthentication().getPrincipal();
        final AccessToken keycloakToken = principal.getKeycloakSecurityContext().getToken();
        final UserAccount account = new UserAccount();
        account.setFirstName(keycloakToken.getGivenName());
        account.setLastName(keycloakToken.getFamilyName());
        account.setUsername(keycloakToken.getPreferredUsername());
        context.getAuthentication().getAuthorities().stream().map(ga -> UserRole.fromRoleName(ga.getAuthority()))
               .filter(r -> !r.getType().isEmpty()).forEach(r -> account.addType(r.getType()));
        account.setUri(idResolver
                .generateIdentifier(ConfigParam.NAMESPACE_USER, account.getFirstName(), account.getLastName()));
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
        return context.getAuthentication().isAuthenticated();
    }

    /**
     * @see #authenticated()
     */
    public boolean isAuthenticated() {
        return authenticated();
    }

    /**
     * Creates an authentication token based on the specified user details and sets it to the current thread's security
     * context.
     *
     * @param userDetails Details of the user to set as current
     * @return The generated authentication token
     */
    public AuthenticationToken setCurrentUser(TermItUserDetails userDetails) {
        final AuthenticationToken token = new AuthenticationToken(userDetails.getAuthorities(), userDetails);
        token.setAuthenticated(true);

        final SecurityContext context = new SecurityContextImpl();
        context.setAuthentication(token);
        SecurityContextHolder.setContext(context);
        return token;
    }

    /**
     * Checks that the specified password corresponds to the current user's password.
     *
     * @param password The password to verify
     * @throws IllegalArgumentException When the password's do not match
     */
    public void verifyCurrentUserPassword(String password) {
        final UserAccount currentUser = getCurrentUser();
        if (!passwordEncoder.matches(password, currentUser.getPassword())) {
            throw new ValidationException("The specified password does not match the original one.");
        }
    }

    /**
     * Verifies that the specified user is enabled and not locked.
     *
     * @param user User to check
     */
    public static void verifyAccountStatus(UserAccount user) {
        Objects.requireNonNull(user);
        if (user.isLocked()) {
            throw new LockedException("Account of user " + user + " is locked.");
        }
        if (!user.isEnabled()) {
            throw new DisabledException("Account of user " + user + " is disabled.");
        }
    }
}
