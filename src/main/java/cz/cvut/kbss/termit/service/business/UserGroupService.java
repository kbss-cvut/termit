package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.UserGroup;

import java.net.URI;
import java.util.Collection;

/**
 * Manages groups of users.
 */
public interface UserGroupService extends CrudService<UserGroup, UserGroup> {

    User findRequiredUser(URI uri);

    /**
     * Adds the specified users to the specified target group.
     *
     * @param target Target user group
     * @param toAdd  Users to add to the group
     */
    void addMembers(UserGroup target, Collection<User> toAdd);

    /**
     * Removes the specified users from the specified target group.
     *
     * @param target   Target user group
     * @param toRemove Users to remove from the group
     */
    void removeMembers(UserGroup target, Collection<User> toRemove);
}
