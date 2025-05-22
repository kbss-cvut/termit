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
package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.UserGroup;
import cz.cvut.kbss.termit.persistence.dao.GenericDao;
import cz.cvut.kbss.termit.persistence.dao.UserGroupDao;
import cz.cvut.kbss.termit.security.SecurityConstants;
import cz.cvut.kbss.termit.service.business.UserGroupService;
import cz.cvut.kbss.termit.util.Utils;
import jakarta.annotation.Nonnull;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Service
public class UserGroupRepositoryService extends BaseRepositoryService<UserGroup, UserGroup>
        implements UserGroupService {

    private static final Logger LOG = LoggerFactory.getLogger(UserGroupRepositoryService.class);

    private final UserGroupDao dao;

    private final UserRepositoryService userService;

    public UserGroupRepositoryService(Validator validator, UserGroupDao dao, UserRepositoryService userService) {
        super(validator);
        this.dao = dao;
        this.userService = userService;
    }

    @Override
    protected GenericDao<UserGroup> getPrimaryDao() {
        return dao;
    }

    @Override
    protected UserGroup mapToDto(UserGroup entity) {
        return entity;
    }

    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_ADMIN + "')")
    @Transactional
    @Override
    public void addMembers(UserGroup target, Collection<User> toAdd) {
        Objects.requireNonNull(target);
        Objects.requireNonNull(toAdd);
        if (toAdd.isEmpty()) {
            return;
        }
        LOG.debug("Adding users {} to group {}.", toAdd, target);
        toAdd.forEach(target::addMember);
        dao.update(target);
    }

    @PreAuthorize("hasRole('" + SecurityConstants.ROLE_ADMIN + "')")
    @Transactional
    @Override
    public void removeMembers(UserGroup target, Collection<User> toRemove) {
        Objects.requireNonNull(target);
        Objects.requireNonNull(toRemove);
        if (toRemove.isEmpty()) {
            return;
        }
        LOG.debug("Removing users {} from group {}.", toRemove, target);
        final Set<User> removeSet = new HashSet<>(toRemove);
        Utils.emptyIfNull(target.getMembers()).removeIf(removeSet::contains);
        dao.update(target);
    }

    @Override
    public User findRequiredUser(URI uri) {
        return userService.findRequired(uri).toUser();
    }

    @Override
    protected void postRemove(@Nonnull UserGroup instance) {
        super.postRemove(instance);
        // TODO Remove group from ACLs
    }
}
