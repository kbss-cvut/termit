package cz.cvut.kbss.termit.service.security.authorization.acl;

import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.model.acl.AccessControlList;
import cz.cvut.kbss.termit.model.acl.AccessControlRecord;
import cz.cvut.kbss.termit.model.acl.AccessLevel;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.security.model.UserRole;
import cz.cvut.kbss.termit.service.business.AccessControlListService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

/**
 * Authorizes access to a resource based on its {@link cz.cvut.kbss.termit.model.acl.AccessControlList}.
 */
@Service
public class AccessControlListBasedAuthorizationService {

    private static final Logger LOG = LoggerFactory.getLogger(AccessControlListBasedAuthorizationService.class);

    private final AccessControlListService aclService;

    public AccessControlListBasedAuthorizationService(AccessControlListService aclService) {
        this.aclService = aclService;
    }

    /**
     * Checks whether the specified user can read the specified resource.
     * <p>
     * Read access means that the user may view the specified resource in a collection of resources as well as access
     * the details of the specified resource.
     *
     * @param user     User to authorize
     * @param resource Resource access to which is to be authorized
     * @return {@code true} if read access is authorized for the specified user, {@code false} otherwise
     */
    public boolean canRead(UserAccount user, HasIdentifier resource) {
        return hasAccessLevel(AccessLevel.READ, user, resource);
    }

    /**
     * Checks whether the specified resource can be read anonymously.
     * <p>
     * That is, if the resource is readable without a user being logged in. The implementation checks for access level
     * of user role {@link cz.cvut.kbss.termit.security.model.UserRole#RESTRICTED_USER} - if it is {@link
     * AccessLevel#NONE}, anonymous access is denied as well. Otherwise, anonymous read access is allowed.
     *
     * @param resource Resource access to which is to be authorized
     * @return {@code true} if read access is authorized for anonymous user, {@code false} otherwise
     */
    public boolean canReadAnonymously(HasIdentifier resource) {
        Objects.requireNonNull(resource);
        final Optional<AccessControlList> optionalAcl = aclService.findFor(resource);
        if (optionalAcl.isEmpty()) {
            LOG.warn("Asset {} is missing an ACL.", resource);
        }
        final Optional<AccessControlRecord<?>> record = optionalAcl.flatMap(acl -> acl.getRecords().stream()
                                                                                      .filter(r -> r.getHolder()
                                                                                                    .getUri()
                                                                                                    .toString()
                                                                                                    .equals(UserRole.RESTRICTED_USER.getType()))
                                                                                      .findAny());
        return record.map(r -> r.getAccessLevel().includes(AccessLevel.READ)).orElse(false);
    }

    /**
     * Checks whether the specified user has the specified level of access to the specified resource.
     *
     * @param expected Expected access level
     * @param user     User whose access to validate
     * @param resource Target resource to which access is to be validated
     * @return {@code true} if user has access to the target resource, {@code false} otherwise
     */
    public boolean hasAccessLevel(AccessLevel expected, UserAccount user, HasIdentifier resource) {
        Objects.requireNonNull(expected);
        Objects.requireNonNull(user);
        Objects.requireNonNull(resource);

        return getAccessLevel(user, resource).includes(expected);
    }

    /**
     * Gets the highest level of access of the specified user to the specified resource.
     *
     * @param user     User whose access level to resolve
     * @param resource Target resource access to which is to be determined
     * @return Highest level of access held by the specified user
     */
    public AccessLevel getAccessLevel(UserAccount user, HasIdentifier resource) {
        Objects.requireNonNull(user);
        Objects.requireNonNull(resource);
        if (user.isAdmin()) {
            // Admin has always full access
            return AccessLevel.SECURITY;
        }
        final Optional<AccessControlList> optionalAcl = aclService.findFor(resource);
        return optionalAcl.map(accessControlList -> accessControlList.getRecords().stream()
                                                                     .map(r -> r.getAccessLevelFor(user))
                                                                     .flatMap(Optional::stream)
                                                                     .max(Comparator.comparing(AccessLevel::ordinal))
                                                                     .orElse(AccessLevel.NONE))
                          .orElse(AccessLevel.NONE);
    }

    /**
     * Checks whether the specified user can modify the specified resource.
     * <p>
     * Note that modification does not include removal, that is handled separately by {@link #canRead(UserAccount,
     * HasIdentifier)}.
     *
     * @param user     User to authorize
     * @param resource Resource access to which is to be authorized
     * @return {@code true} if the specified user can modify the specified resource, {@code false} otherwise
     * @see #canRemove(UserAccount, HasIdentifier)
     */
    public boolean canModify(UserAccount user, HasIdentifier resource) {
        return hasAccessLevel(AccessLevel.WRITE, user, resource);
    }

    /**
     * Checks whether the specified user can remove the specified resource.
     * <p>
     * Note that this checks only authorization conditions, there may be other domain conditions for removal.
     *
     * @param user     User to authorize
     * @param resource Resource access to which is to be authorized
     * @return {@code true} if the specified user can remove the specified resource, {@code false} otherwise
     */
    public boolean canRemove(UserAccount user, HasIdentifier resource) {
        return hasAccessLevel(AccessLevel.SECURITY, user, resource);
    }
}
