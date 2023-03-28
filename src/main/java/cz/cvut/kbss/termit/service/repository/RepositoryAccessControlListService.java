package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.UserRole;
import cz.cvut.kbss.termit.model.acl.*;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.persistence.dao.acl.AccessControlListDao;
import cz.cvut.kbss.termit.service.business.AccessControlListService;
import cz.cvut.kbss.termit.service.security.SecurityUtils;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class RepositoryAccessControlListService implements AccessControlListService {

    private static final Logger LOG = LoggerFactory.getLogger(RepositoryAccessControlListService.class);

    private final AccessControlListDao dao;

    private final ChangeRecordService changeRecordService;

    private final UserRoleRepositoryService userRoleService;

    private final Configuration.ACL aclConfig;

    public RepositoryAccessControlListService(AccessControlListDao dao, ChangeRecordService changeRecordService,
                                              UserRoleRepositoryService userRoleService, Configuration config) {
        this.dao = dao;
        this.changeRecordService = changeRecordService;
        this.userRoleService = userRoleService;
        this.aclConfig = config.getAcl();
    }

    @Override
    public AccessControlList findRequired(URI id) {
        return dao.find(id).orElseThrow(() -> NotFoundException.create(AccessControlList.class, id));
    }

    @Override
    public AccessControlList getRequiredReference(URI id) {
        return dao.getReference(id).orElseThrow(() -> NotFoundException.create(AccessControlList.class, id));
    }

    @Override
    public Optional<AccessControlList> findFor(HasIdentifier subject) {
        return dao.findFor(subject);
    }

    @Transactional
    @Override
    public AccessControlList createFor(HasIdentifier subject) {
        Objects.requireNonNull(subject);
        LOG.debug("Creating ACL for subject {}.", subject);
        final AccessControlList acl = new AccessControlList();
        setInitialAccessControlRecords(subject, acl);
        dao.persist(acl);
        LOG.debug("Created ACL: {}.", acl);
        return acl;
    }

    private void setInitialAccessControlRecords(HasIdentifier subject, AccessControlList acl) {
        // Add current user - author in case the subject is just being created
        if (SecurityUtils.authenticated()) {
            acl.addRecord(new UserAccessControlRecord(AccessLevel.SECURITY, SecurityUtils.currentUser().toUser()));
        }
        // Add possible authors in case the subject already existed
        changeRecordService.getAuthors(subject)
                           .forEach(u -> acl.addRecord(new UserAccessControlRecord(AccessLevel.SECURITY, u)));
        // Add records with configured access level for reader and editor user roles
        final List<UserRole> roles = userRoleService.findAll();
        roles.stream().filter(ur -> cz.cvut.kbss.termit.security.model.UserRole.FULL_USER.getType().equals(ur.getUri()
                                                                                                             .toString()))
             .findAny().ifPresent(
                     editor -> acl.addRecord(new RoleAccessControlRecord(aclConfig.getDefaultEditorAccessLevel(), editor)));
        roles.stream()
             .filter(ur -> cz.cvut.kbss.termit.security.model.UserRole.RESTRICTED_USER.getType().equals(ur.getUri()
                                                                                                          .toString()))
             .findAny().ifPresent(
                     editor -> acl.addRecord(new RoleAccessControlRecord(aclConfig.getDefaultReaderAccessLevel(), editor)));
    }

    @Transactional
    @Override
    public void addRecords(AccessControlList acl, Collection<AccessControlRecord<?>> records) {
        Objects.requireNonNull(acl);
        Objects.requireNonNull(records);
        if (records.isEmpty()) {
            return;
        }
        final AccessControlList toUpdate = findRequired(acl.getUri());
        LOG.debug("Adding records {} to ACL {}.", records, toUpdate);
        records.forEach(toUpdate::addRecord);
        // Explicitly update to trigger merge of the new records
        dao.update(toUpdate);
    }

    @Transactional
    @Override
    public void removeRecords(AccessControlList acl, Collection<AccessControlRecord<?>> records) {
        Objects.requireNonNull(acl);
        Objects.requireNonNull(records);
        if (records.isEmpty()) {
            return;
        }
        final AccessControlList toUpdate = findRequired(acl.getUri());
        LOG.debug("Removing records {} from ACL {}.", records, toUpdate);
        assert toUpdate.getRecords() != null;
        records.forEach(r -> toUpdate.getRecords().removeIf(acr -> Objects.equals(acr.getUri(), r.getUri())));
        // Explicitly update to remove orphans
        dao.update(toUpdate);
    }

    @Transactional
    @Override
    public void updateRecordAccessLevel(AccessControlList acl, AccessControlRecord<?> record) {
        Objects.requireNonNull(acl);
        Objects.requireNonNull(record);
        final AccessControlList toUpdate = findRequired(acl.getUri());

        Utils.emptyIfNull(acl.getRecords()).stream().filter(acr -> Objects.equals(acr.getUri(), record.getUri()))
             .findAny().ifPresent(r -> {
                 LOG.debug("Updating access level from {} to {} in record {} in ACL {}.", r.getAccessLevel(),
                           record.getAccessLevel(), Utils.uriToString(record.getUri()), toUpdate);
                 r.setAccessLevel(record.getAccessLevel());
             });
    }
}