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
