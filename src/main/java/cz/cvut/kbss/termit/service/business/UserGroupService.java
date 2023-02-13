package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.termit.model.UserGroup;

import java.net.URI;

/**
 * Manages groups of users.
 */
public interface UserGroupService extends CrudService<UserGroup, UserGroup> {

    /**
     * Adds users with the specified identifiers to the specified target group.
     *
     * @param target Target user group
     * @param toAdd  Identifiers of users to add to the group
     */
    void addUsers(UserGroup target, URI... toAdd);

    /**
     * Removes users with the specified identifiers from the specified target group.
     *
     * @param target   Target user group
     * @param toRemove Identifiers of users to remove from the group
     */
    void removeUsers(UserGroup target, URI... toRemove);
}
