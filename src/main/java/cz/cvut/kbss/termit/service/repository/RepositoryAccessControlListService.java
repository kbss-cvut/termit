package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.termit.dto.acl.AccessControlListDto;
import cz.cvut.kbss.termit.dto.mapper.DtoMapper;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.exception.UnsupportedOperationException;
import cz.cvut.kbss.termit.model.AccessControlAgent;
import cz.cvut.kbss.termit.model.Asset;
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
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@CacheConfig(cacheNames = "acls")
@Service
public class RepositoryAccessControlListService implements AccessControlListService {

    private static final Logger LOG = LoggerFactory.getLogger(RepositoryAccessControlListService.class);

    private final AccessControlListDao dao;

    private final ChangeRecordService changeRecordService;

    private final UserRoleRepositoryService userRoleService;

    private final DtoMapper dtoMapper;

    private final SecurityUtils securityUtils;

    private final Configuration.ACL aclConfig;

    public RepositoryAccessControlListService(AccessControlListDao dao, ChangeRecordService changeRecordService,
                                              UserRoleRepositoryService userRoleService,
                                              DtoMapper dtoMapper, SecurityUtils securityUtils, Configuration config) {
        this.dao = dao;
        this.changeRecordService = changeRecordService;
        this.userRoleService = userRoleService;
        this.dtoMapper = dtoMapper;
        this.securityUtils = securityUtils;
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

    @Cacheable(key = "#p0.uri")
    @Override
    public Optional<AccessControlList> findFor(HasIdentifier subject) {
        return dao.findFor(subject);
    }

    @Override
    public Optional<AccessControlListDto> findForAsDto(HasIdentifier subject) {
        return findFor(subject).map(dtoMapper::accessControlListToDto);
    }

    @CachePut(key = "#p0.uri")
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
            acl.addRecord(new UserAccessControlRecord(AccessLevel.SECURITY, securityUtils.getCurrentUser().toUser()));
        }
        // Add possible authors in case the subject already existed
        changeRecordService.getAuthors(subject)
                           .forEach(u -> acl.addRecord(new UserAccessControlRecord(AccessLevel.SECURITY, u)));
        // Add record with configured access level for reader and editor user roles
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

    @CacheEvict(keyGenerator = "accessControlListCacheKeyGenerator")
    @Transactional
    @Override
    public void remove(AccessControlList acl) {
        Objects.requireNonNull(acl);
        LOG.debug("Removing ACL {}.", acl);
        dao.remove(acl);
    }

    @Override
    public AccessControlList clone(AccessControlList original) {
        Objects.requireNonNull(original);
        LOG.debug("Creating a new ACL by cloning {}.", original);
        final AccessControlList clone = new AccessControlList();
        clone.setRecords(Utils.emptyIfNull(original.getRecords()).stream().map(AccessControlRecord::copy)
                              .collect(Collectors.toSet()));
        dao.persist(clone);
        return clone;
    }

    @CacheEvict(keyGenerator = "accessControlListCacheKeyGenerator")
    @Transactional
    @Override
    public void addRecord(AccessControlList acl, AccessControlRecord<?> record) {
        Objects.requireNonNull(acl);
        Objects.requireNonNull(record);
        final AccessControlList toUpdate = findRequired(acl.getUri());
        LOG.debug("Adding record {} to ACL {}.", record, toUpdate);
        toUpdate.addRecord(record);
        // Explicitly update to trigger merge of the new record
        dao.update(toUpdate);
    }

    @CacheEvict(keyGenerator = "accessControlListCacheKeyGenerator")
    @Transactional
    @Override
    public void removeRecord(AccessControlList acl, AccessControlRecord<?> record) {
        Objects.requireNonNull(acl);
        Objects.requireNonNull(record);

        final AccessControlList toUpdate = findRequired(acl.getUri());
        LOG.debug("Removing record {} from ACL {}.", record, toUpdate);
        assert toUpdate.getRecords() != null;
        toUpdate.getRecords().removeIf(acr -> Objects.equals(acr.getUri(), record.getUri()));
        verifyUserRoleRecordsArePresent(toUpdate);
        // Explicitly update to remove orphans
        dao.update(toUpdate);
    }

    private void verifyUserRoleRecordsArePresent(AccessControlList acl) {
        boolean readerFound = false;
        boolean editorFound = false;
        for (AccessControlRecord<?> record : acl.getRecords()) {
            final String holderIri = record.getHolder().getUri().toString();
            if (Objects.equals(cz.cvut.kbss.termit.security.model.UserRole.RESTRICTED_USER.getType(), holderIri)) {
                readerFound = true;
            } else if (Objects.equals(cz.cvut.kbss.termit.security.model.UserRole.FULL_USER.getType(), holderIri)) {
                editorFound = true;
            }
        }
        if (!readerFound || !editorFound) {
            throw new UnsupportedOperationException(
                    "Access control list must contain a record for user roles " + cz.cvut.kbss.termit.security.model.UserRole.RESTRICTED_USER + " and " + cz.cvut.kbss.termit.security.model.UserRole.FULL_USER);
        }
    }

    @CacheEvict(keyGenerator = "accessControlListCacheKeyGenerator")
    @Transactional
    @Override
    public void updateRecordAccessLevel(AccessControlList acl, AccessControlRecord<?> record) {
        Objects.requireNonNull(acl);
        Objects.requireNonNull(record);
        final AccessControlList toUpdate = findRequired(acl.getUri());

        Utils.emptyIfNull(toUpdate.getRecords()).stream().filter(acr -> Objects.equals(acr.getUri(), record.getUri()))
             .findAny().ifPresent(r -> {
                 LOG.debug("Updating access level from {} to {} in record {} in ACL {}.", r.getAccessLevel(),
                           record.getAccessLevel(), Utils.uriToString(record.getUri()), toUpdate);
                 r.setAccessLevel(record.getAccessLevel());
             });
    }

    @Transactional(readOnly = true)
    @Override
    public List<? extends Asset<?>> findAssetsByAgentWithSecurityAccess(@NonNull AccessControlAgent agent) {
        return dao.findAssetsByAgentWithSecurityAccess(agent);
    }
}
