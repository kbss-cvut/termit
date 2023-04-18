package cz.cvut.kbss.termit.service.security.authorization.acl;

import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.model.acl.AccessControlList;
import cz.cvut.kbss.termit.model.acl.AccessLevel;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.service.business.AccessControlListService;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Authorizes access to a resource based on its {@link cz.cvut.kbss.termit.model.acl.AccessControlList}.
 */
@Service
public class AccessControlListBasedAuthorizationService {

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

        if (user.isAdmin()) {
            // Admin has always full access
            return true;
        }
        final Optional<AccessControlList> optionalAcl = aclService.findFor(resource);
        if (optionalAcl.isPresent()) {
            final Set<AccessLevel> levels = optionalAcl.get().getRecords().stream().map(r -> r.getAccessLevelFor(user))
                                                       .flatMap(Optional::stream).collect(Collectors.toSet());
            return levels.stream().anyMatch(level -> level.includes(expected));
        }
        return false;
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
