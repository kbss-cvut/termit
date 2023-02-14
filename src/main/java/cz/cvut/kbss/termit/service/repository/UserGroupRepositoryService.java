package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.UserGroup;
import cz.cvut.kbss.termit.persistence.dao.GenericDao;
import cz.cvut.kbss.termit.persistence.dao.UserGroupDao;
import cz.cvut.kbss.termit.security.SecurityConstants;
import cz.cvut.kbss.termit.service.business.UserGroupService;
import cz.cvut.kbss.termit.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.validation.Validator;
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
        if (toAdd.size() == 0) {
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
        if (toRemove.size() == 0) {
            return;
        }
        LOG.debug("Removing users {} from group {}.", toRemove, target);
        final Set<User> removeSet = new HashSet<>(toRemove);
        Utils.emptyIfNull(target.getMembers()).removeIf(removeSet::contains);
        dao.update(target);
    }

    @Override
    public User getRequiredUserReference(URI uri) {
        return userService.getRequiredReference(uri).toUser();
    }

    @Override
    protected void postRemove(@NonNull UserGroup instance) {
        super.postRemove(instance);
        // TODO Remove group from ACLs
    }
}
