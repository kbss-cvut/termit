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
package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.termit.dto.PasswordChangeDto;
import cz.cvut.kbss.termit.model.RdfsResource;
import cz.cvut.kbss.termit.dto.mapper.DtoMapper;
import cz.cvut.kbss.termit.event.LoginAttemptsThresholdExceeded;
import cz.cvut.kbss.termit.exception.AuthorizationException;
import cz.cvut.kbss.termit.exception.InvalidPasswordChangeRequestException;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.exception.UnsupportedOperationException;
import cz.cvut.kbss.termit.exception.ValidationException;
import cz.cvut.kbss.termit.model.PasswordChangeRequest;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.model.UserRole;
import cz.cvut.kbss.termit.rest.dto.UserUpdateDto;
import cz.cvut.kbss.termit.service.notification.PasswordChangeNotifier;
import cz.cvut.kbss.termit.service.repository.PasswordChangeRequestRepositoryService;
import cz.cvut.kbss.termit.service.repository.UserRepositoryService;
import cz.cvut.kbss.termit.service.repository.UserRoleRepositoryService;
import cz.cvut.kbss.termit.service.security.SecurityUtils;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Utils;
import cz.cvut.kbss.termit.util.Vocabulary;
import jakarta.annotation.Nonnull;
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
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * User account-related business logic.
 */
@Service
public class UserService {

    private static final Logger LOG = LoggerFactory.getLogger(UserService.class);
    public static final String INVALID_TOKEN_ERROR_MESSAGE_ID = "resetPassword.invalidToken";

    private final UserRepositoryService repositoryService;

    private final UserRoleRepositoryService userRoleRepositoryService;

    private final AccessControlListService aclService;

    private final SecurityUtils securityUtils;

    private final DtoMapper dtoMapper;

    private final Configuration.Security securityConfig;

    private final PasswordChangeRequestRepositoryService passwordChangeRequestRepositoryService;

    private final PasswordChangeNotifier passwordChangeNotifier;

    @Autowired
    public UserService(UserRepositoryService repositoryService, UserRoleRepositoryService userRoleRepositoryService,
                       AccessControlListService aclService, SecurityUtils securityUtils, DtoMapper dtoMapper,
                       Configuration configuration,
                       PasswordChangeRequestRepositoryService passwordChangeRequestRepositoryService,
                       PasswordChangeNotifier passwordChangeNotifier) {
        this.repositoryService = repositoryService;
        this.userRoleRepositoryService = userRoleRepositoryService;
        this.aclService = aclService;
        this.securityUtils = securityUtils;
        this.dtoMapper = dtoMapper;
        this.securityConfig = configuration.getSecurity();
        this.passwordChangeRequestRepositoryService = passwordChangeRequestRepositoryService;
        this.passwordChangeNotifier = passwordChangeNotifier;
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
     * Gets a reference (with empty attribute values) to the user with the specified id.
     * @param id User identifier
     * @return Reference to the matching user account
     * @throws NotFoundException When no matching account is found
     */
    public UserAccount getReference(URI id) {
        return repositoryService.getReference(id);
    }

    /**
     * Retrieves currently logged-in user.
     * <p>
     * It also updates last seen timestamp of the current user. While this is a side effect in a get method, it is a
     * simpler solution that emitting some kind of event and using that.
     *
     * @return Currently logged-in user's account
     */
    @Transactional
    public UserAccount getCurrent() {
        final UserAccount account = securityUtils.getCurrentUser();
        account.erasePassword();
        updateLastSeen(account.copy());
        return account;
    }

    private void updateLastSeen(UserAccount account) {
        account.setLastSeen(Utils.timestamp());
        LOG.trace("Updating last seen timestamp of user {}.", account);
        repositoryService.update(account);
    }

    /**
     * Persists a new user.
     * When a password is null or blank,
     * a random password is generated and an email for password creation is sent to the user.
     * @param account
     */
    @Transactional
    public void adminCreateUser(UserAccount account) {
        if (account.getPassword() == null || account.getPassword().isBlank()) {
            // generate random password
            account.setPassword(UUID.randomUUID() + UUID.randomUUID().toString());
            account.lock();
            persist(account);

            PasswordChangeRequest passwordChangeRequest = createPasswordChangeRequest(account);
            passwordChangeNotifier.sendCreatePasswordEmail(passwordChangeRequest);
        } else {
            persist(account);
        }
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
        UserAccount currentUser = getCurrentUser(update);
        if (!Objects.equals(currentUser.getTypes(), update.getTypes())) {
            throw new ValidationException(
                    "User " + securityUtils.getCurrentUser() + " attempted to update their role.");
        }
        if (update.getPassword() != null) {
            securityUtils.verifyCurrentUserPassword(update.getOriginalPassword());
        }
        repositoryService.update(update.asUserAccount());
    }

    @Nonnull
    private UserAccount getCurrentUser(UserUpdateDto update) {
        UserAccount currentUser = securityUtils.getCurrentUser();

        if (!currentUser.getUri().equals(update.getUri())) {
            throw new AuthorizationException(
                    "User " + securityUtils.getCurrentUser() + " attempted to update a different user's account.");
        }
        if (!currentUser.getUsername().equals(update.getUsername())) {
            throw new ValidationException(
                    "User " + securityUtils.getCurrentUser() + " attempted to update their username.");
        }
        return currentUser;
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
        if (securityUtils.isAuthenticated() && securityUtils.getCurrentUser().equals(account)) {
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

    /**
     * Gets a list of assets the specified user manages.
     * <p>
     * By managing it is meant that the user has {@link cz.cvut.kbss.termit.model.acl.AccessLevel#SECURITY} access level
     * to them.
     *
     * @param user User whose access to check
     * @return List of RDFS resources representing the managed assets
     */
    public List<RdfsResource> getManagedAssets(@Nonnull UserAccount user) {
        Objects.requireNonNull(user);
        return aclService.findAssetsByAgentWithSecurityAccess(user.toUser()).stream()
                         .map(dtoMapper::assetToRdfsResource).collect(Collectors.toList());
    }

    private PasswordChangeRequest createPasswordChangeRequest(UserAccount userAccount) {
        // delete any existing request for the user
        passwordChangeRequestRepositoryService.findAllByUserAccount(userAccount)
                                              .forEach(passwordChangeRequestRepositoryService::remove);

        return passwordChangeRequestRepositoryService.create(userAccount);
    }

    @Transactional
    public void requestPasswordReset(String username) {
        final UserAccount account = repositoryService.findByUsername(username)
                                               .orElseThrow(() -> NotFoundException.create(UserAccount.class, username));
        PasswordChangeRequest request = createPasswordChangeRequest(account);
        passwordChangeNotifier.sendPasswordResetEmail(request);
    }

    private boolean isValid(PasswordChangeRequest request) {
        return request.getCreatedAt().plus(securityConfig.getPasswordChangeRequestValidity()).isAfter(Utils.timestamp());
    }

    /**
     * Changes the user's password if there is a valid password change request.
     * Unlocks the user account if it is locked.
     */
    @Transactional
    public void changePassword(PasswordChangeDto passwordChangeDto) {
        Supplier<AuthorizationException> exception = () -> new InvalidPasswordChangeRequestException("Invalid or expired password change link", INVALID_TOKEN_ERROR_MESSAGE_ID);
        PasswordChangeRequest request = passwordChangeRequestRepositoryService.find(passwordChangeDto.getUri())
                                                                              .orElseThrow(exception);

        if (!request.getToken().equals(passwordChangeDto.getToken()) || !isValid(request)) {
            throw exception.get();
        }

        UserAccount account = repositoryService.find(request.getUserAccount().getUri())
                                                   .orElseThrow(exception);

        passwordChangeRequestRepositoryService.remove(request);

        unlock(account, passwordChangeDto.getNewPassword());
        LOG.info("Password changed for user {}.", account.getUsername());
    }
}
