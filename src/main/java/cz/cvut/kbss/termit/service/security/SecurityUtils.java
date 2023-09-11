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
import cz.cvut.kbss.termit.security.HierarchicalRoleBasedAuthorityMapper;
import cz.cvut.kbss.termit.security.model.AuthenticationToken;
import cz.cvut.kbss.termit.security.model.TermItUserDetails;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.util.Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Handle user session-related functions.
 */
@Service
public class SecurityUtils {

    private final UserDetailsService userDetailsService;

    private final PasswordEncoder passwordEncoder;

    private final IdentifierResolver idResolver;

    private final Configuration.Namespace configuration;

    @Autowired
    public SecurityUtils(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder,
                         IdentifierResolver idResolver, Configuration configuration) {
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.idResolver = idResolver;
        this.configuration = configuration.getNamespace();
    }

    /**
     * Gets the currently authenticated user.
     *
     * @return Current user
     */
    public UserAccount getCurrentUser() {
        final SecurityContext context = SecurityContextHolder.getContext();
        assert context != null && context.getAuthentication().isAuthenticated();
        if (context.getAuthentication().getPrincipal() instanceof Jwt) {
            return resolveAccountFromOAuthPrincipal(context);
        } else {
            final TermItUserDetails userDetails = (TermItUserDetails) context.getAuthentication().getDetails();
            return userDetails.getUser();
        }
    }

    private UserAccount resolveAccountFromOAuthPrincipal(SecurityContext context) {
        final Jwt principal = (Jwt) context.getAuthentication().getPrincipal();
        final UserAccount account = new UserAccount();
        final OidcUserInfo userInfo = new OidcUserInfo(principal.getClaims());
        account.setFirstName(userInfo.getGivenName());
        account.setLastName(userInfo.getFamilyName());
        account.setUsername(userInfo.getPreferredUsername());
        HierarchicalRoleBasedAuthorityMapper.resolveUserRolesFromAuthorities(
                context.getAuthentication().getAuthorities()).forEach(r -> account.addType(r.getType()));
        account.setUri(idResolver.generateIdentifier(configuration.getUser(), account.getFullName()));
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
        return context.getAuthentication() != null && !(context.getAuthentication() instanceof AnonymousAuthenticationToken);
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
    public static AuthenticationToken setCurrentUser(TermItUserDetails userDetails) {
        final AuthenticationToken token = new AuthenticationToken(userDetails.getAuthorities(), userDetails);
        token.setAuthenticated(true);

        final SecurityContext context = new SecurityContextImpl();
        context.setAuthentication(token);
        SecurityContextHolder.setContext(context);
        return token;
    }

    /**
     * Reloads the current user's data from the database.
     */
    public void updateCurrentUser() {
        final TermItUserDetails updateDetails = (TermItUserDetails) userDetailsService.loadUserByUsername(
                getCurrentUser().getUsername());
        setCurrentUser(updateDetails);
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
