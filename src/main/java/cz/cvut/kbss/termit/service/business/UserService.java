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
package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.termit.event.LoginAttemptsThresholdExceeded;
import cz.cvut.kbss.termit.exception.AuthorizationException;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.exception.UnsupportedOperationException;
import cz.cvut.kbss.termit.exception.ValidationException;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.model.UserRole;
import cz.cvut.kbss.termit.rest.dto.UserUpdateDto;
import cz.cvut.kbss.termit.service.repository.UserRepositoryService;
import cz.cvut.kbss.termit.service.repository.UserRoleRepositoryService;
import cz.cvut.kbss.termit.service.security.SecurityUtils;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * User account-related business logic.
 */
@Service
public class UserService {

    private static final Logger LOG = LoggerFactory.getLogger(UserService.class);

    private final UserRepositoryService repositoryService;

    private final UserRoleRepositoryService userRoleRepositoryService;

    private final SecurityUtils securityUtils;

    @Autowired
    public UserService(UserRepositoryService repositoryService,
                       UserRoleRepositoryService userRoleRepositoryService,
                       SecurityUtils securityUtils) {
        this.repositoryService = repositoryService;
        this.userRoleRepositoryService = userRoleRepositoryService;
        this.securityUtils = securityUtils;
    }

    /**
     * Gets accounts of all users in the system.
     *
     * @return List of user accounts ordered by last name and first name
     */
    public List<UserAccount> findAll() {
        return repositoryService.findAll();
    }

    /**
     * Finds a user with the specified id.
     *
     * @param id User identifier
     * @return Matching user wrapped in an {@code Optional}
     */
    public Optional<UserAccount> find(URI id) {
        return repositoryService.find(id);
    }

    /**
     * Finds a user with the specified id.
     *
     * @param id User identifier
     * @return Matching user account
     * @throws NotFoundException When no matching account is found
     */
    public UserAccount findRequired(URI id) {
        return repositoryService.findRequired(id);
    }

    /**
     * Retrieves currently logged in user.
     *
     * @return Currently logged in user's account
     */
    public UserAccount getCurrent() {
        final UserAccount account = securityUtils.getCurrentUser();
        account.erasePassword();
        return account;
    }

    /**
     * Persists the specified user account.
     *
     * @param account Account to save
     */
    @Transactional
    public void persist(UserAccount account) {
        Objects.requireNonNull(account);
        LOG.trace("Persisting user account {}.", account);
        if (!securityUtils.isAuthenticated() || !securityUtils.getCurrentUser().isAdmin()) {
            account.addType(Vocabulary.s_c_omezeny_uzivatel_termitu);
            account.removeType(Vocabulary.s_c_administrator_termitu);
        }
        if (account.getPassword() == null || account.getPassword().trim().isEmpty()) {
            throw new ValidationException("User password must not be empty!");
        }
        repositoryService.persist(account);
    }

    /**
     * Updates current user's account with the specified update data.
     * <p>
     * If the update contains also password update, the original password specified in the update object has to match
     * current user's password.
     *
     * @param update Account update data
     * @throws AuthorizationException If the update data concern other than the current user
     */
    @Transactional
    public void updateCurrent(UserUpdateDto update) {
        LOG.trace("Updating current user account.");
        Objects.requireNonNull(update);
        UserAccount currentUser = securityUtils.getCurrentUser();

        if (!currentUser.getUri().equals(update.getUri())) {
            throw new AuthorizationException(
                    "User " + securityUtils.getCurrentUser() + " attempted to update a different user's account.");
        }
        if (!currentUser.getUsername().equals(update.getUsername())) {
            throw new ValidationException(
                "User " + securityUtils.getCurrentUser() + " attempted to update his username.");
        }
        if (currentUser.getTypes() != update.getTypes()
            || (currentUser.getTypes() != null && currentUser.getTypes().equals(update.getTypes()))) {
            throw new ValidationException(
                "User " + securityUtils.getCurrentUser() + " attempted to update his role.");
        }
        if (update.getPassword() != null) {
            securityUtils.verifyCurrentUserPassword(update.getOriginalPassword());
        }
        repositoryService.update(update.asUserAccount());
    }

    /**
     * Unlocks the specified user account.
     * <p>
     * The specified password is set as the new password of the user account.
     *
     * @param account     Account to unlock
     * @param newPassword New password for the unlocked account
     */
    @Transactional
    public void unlock(UserAccount account, String newPassword) {
        Objects.requireNonNull(account);
        Objects.requireNonNull(newPassword);
        ensureNotOwnAccount(account, "unlock");
        LOG.trace("Unlocking user account {}.", account);
        account.unlock();
        account.setPassword(newPassword);
        repositoryService.update(account);
    }

    private void ensureNotOwnAccount(UserAccount account, String operation) {
        if (securityUtils.getCurrentUser().equals(account)) {
            throw new UnsupportedOperationException("Cannot " + operation + " your own account!");
        }
    }

    /**
     * Disables the specified user account.
     *
     * @param account Account to disable
     */
    @Transactional
    public void disable(UserAccount account) {
        Objects.requireNonNull(account);
        ensureNotOwnAccount(account, "disable");
        LOG.trace("Disabling user account {}.", account);
        account.disable();
        repositoryService.update(account);
    }

    /**
     * Enables the specified user account.
     *
     * @param account Account to enable
     */
    @Transactional
    public void enable(UserAccount account) {
        Objects.requireNonNull(account);
        ensureNotOwnAccount(account, "enable");
        LOG.trace("Enabling user account {}.", account);
        account.enable();
        repositoryService.update(account);
    }

    /**
     * Changes role of the given user.
     *
     * @param account Account to change role for
     * @param roleIri IRI of the role to change
     */
    @Transactional
    public void changeRole(UserAccount account, String roleIri) {
        Objects.requireNonNull(account);
        Objects.requireNonNull(roleIri);
        ensureNotOwnAccount(account, "change role");
        LOG.trace("Changing role of user {} to {}.", account, roleIri);
        List<UserRole> roles = userRoleRepositoryService.findAll();
        roles.forEach(r -> account.removeType(r.getUri().toString()));
        account.addType(roleIri);
        repositoryService.update(account);
    }

    /**
     * Locks user account when unsuccessful login attempts limit is exceeded.
     * <p>
     * This is an application event listener and should not be called directly.
     *
     * @param event The event emitted when login attempts limit is exceeded
     */
    @Transactional
    @EventListener
    public void onLoginAttemptsThresholdExceeded(LoginAttemptsThresholdExceeded event) {
        Objects.requireNonNull(event);
        final UserAccount account = event.getUser();
        LOG.trace("Locking user account {} due to exceeding unsuccessful login attempts limit.", account);
        account.lock();
        repositoryService.update(account);
    }

    /**
     * Checks whether a user account with the specified username exists in the repository.
     *
     * @param username Username to check
     * @return Whether username already exists
     */
    public boolean exists(String username) {
        return repositoryService.exists(username);
    }
}
