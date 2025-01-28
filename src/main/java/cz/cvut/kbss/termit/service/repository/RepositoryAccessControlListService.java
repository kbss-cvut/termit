/*
 * TermIt
 * Copyright (C) 2023 Czech Technical University in Prague
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

import cz.cvut.kbss.termit.dto.acl.AccessControlListDto;
import cz.cvut.kbss.termit.dto.mapper.DtoMapper;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.exception.UnsupportedOperationException;
import cz.cvut.kbss.termit.model.AccessControlAgent;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.UserGroup;
import cz.cvut.kbss.termit.model.UserRole;
import cz.cvut.kbss.termit.model.acl.AccessControlList;
import cz.cvut.kbss.termit.model.acl.AccessControlRecord;
import cz.cvut.kbss.termit.model.acl.AccessLevel;
import cz.cvut.kbss.termit.model.acl.RoleAccessControlRecord;
import cz.cvut.kbss.termit.model.acl.UserAccessControlRecord;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.persistence.dao.acl.AccessControlListDao;
import cz.cvut.kbss.termit.service.business.AccessControlListService;
import cz.cvut.kbss.termit.service.security.SecurityUtils;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Utils;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
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
    public AccessControlList getReference(URI id) {
        return dao.getReference(id).orElseThrow(() -> NotFoundException.create(AccessControlList.class, id));
    }

    @Cacheable(key = "#p0.uri", unless = "#result == null")
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
        roles.stream().filter(RepositoryAccessControlListService::isFullUser)
             .findAny().ifPresent(
                     editor -> acl.addRecord(new RoleAccessControlRecord(aclConfig.getDefaultEditorAccessLevel(), editor)));
        roles.stream()
             .filter(RepositoryAccessControlListService::isRestricted)
             .findAny().ifPresent(
                     editor -> acl.addRecord(new RoleAccessControlRecord(aclConfig.getDefaultReaderAccessLevel(), editor)));
        roles.stream().filter(RepositoryAccessControlListService::isAnonymous)
                .findAny().ifPresent(
                        anonymous -> acl.addRecord(new RoleAccessControlRecord(aclConfig.getDefaultAnonymousAccessLevel(), anonymous)));
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
        validate(toUpdate);
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
        validate(toUpdate);
        // Explicitly update to remove orphans
        dao.update(toUpdate);
    }

    private void verifyUserRoleRecordsArePresent(AccessControlList acl) {
        boolean anonymousFound = false;
        boolean readerFound = false;
        boolean editorFound = false;
        for (AccessControlRecord<?> record : acl.getRecords()) {
            if (record.getHolder() instanceof UserRole role) {
                if (isAnonymous(role)) {
                    anonymousFound = true;
                } else if (isRestricted(role)) {
                    readerFound = true;
                } else if (isFullUser(role)) {
                    editorFound = true;
                }
            }
        }
        if (!readerFound || !editorFound || !anonymousFound) {
            throw new UnsupportedOperationException(
                    "Access control list must contain a record for user roles " + cz.cvut.kbss.termit.security.model.UserRole.RESTRICTED_USER + ", " + cz.cvut.kbss.termit.security.model.UserRole.FULL_USER + " and " + cz.cvut.kbss.termit.security.model.UserRole.ANONYMOUS_USER);
        }
    }

    /**
     * Ensures that the specified ACL is valid.
     * Throws otherwise.
     *
     * @param acl ACL to validate
     * @throws UnsupportedOperationException if the ACL is not valid
     * @see #validate(AccessControlRecord)
     * @see #verifyUserRoleRecordsArePresent(AccessControlList)
     */
    public void validate(AccessControlList acl) {
        verifyUserRoleRecordsArePresent(acl);
        acl.getRecords().forEach(this::validate);
    }

    /**
     * Checks whether the specified role
     * is the {@link cz.cvut.kbss.termit.security.model.UserRole#FULL_USER FULL_USER} role.
     *
     * @param role Role to check
     * @return {@code true} if the role is the full user role, {@code false} otherwise
     */
    public static boolean isFullUser(UserRole role) {
        return cz.cvut.kbss.termit.security.model.UserRole.FULL_USER.getType().equals(role.getUri().toString());
    }

    /**
     * Checks whether the specified role
     * is the {@link cz.cvut.kbss.termit.security.model.UserRole#ANONYMOUS_USER ANONYMOUS_USER} role.
     *
     * @param role Role to check
     * @return {@code true} if the role is the anonymous user role, {@code false} otherwise
     */
    public static boolean isAnonymous(UserRole role) {
        return cz.cvut.kbss.termit.security.model.UserRole.ANONYMOUS_USER.getType()
                                                                         .equals(role.getUri().toString());
    }

    /**
     * Checks whether the specified role
     * is the {@link cz.cvut.kbss.termit.security.model.UserRole#RESTRICTED_USER RESTRICTED_USER} role.
     *
     * @param role Role to check
     * @return {@code true} if the role is the restricted user role, {@code false} otherwise
     */
    public static boolean isRestricted(UserRole role) {
        return cz.cvut.kbss.termit.security.model.UserRole.RESTRICTED_USER.getType()
                                                                          .equals(role.getUri().toString());
    }

    /**
     * Ensures that the specified access control record is valid.
     * Throws otherwise.
     * <p>
     * The record is considered valid if:
     * <ul>
     *     <li>Does not grant greater access level than read to the anonymous user role</li>
     *     <li>Does not grant security access level to the restricted user role</li>
     * </ul>
     *
     * @param controlRecord Access control record to validate
     * @throws UnsupportedOperationException if the record is not valid
     */
    public void validate(AccessControlRecord<?> controlRecord) {
        if (controlRecord.getHolder() instanceof UserRole role) {
            // check that the anonymous user does not have greater access level than READ
            if (isAnonymous(role) && controlRecord.getAccessLevel().compareTo(AccessLevel.READ) > 0) {
                throw new UnsupportedOperationException("Access control record for anonymous user cannot grant greater access level then READ.");
            }
            // check that the reader role does not have SECURITY access level
            if (isRestricted(role) && controlRecord.getAccessLevel().includes(AccessLevel.SECURITY)) {
                throw new UnsupportedOperationException("Access control record for restricted user cannot have access level SECURITY.");
            }

        }
        // check that a user group does not have SECURITY access level
        if (controlRecord.getHolder() instanceof UserGroup &&
                controlRecord.getAccessLevel().includes(AccessLevel.SECURITY)) {
            throw new UnsupportedOperationException("Access control record for user group cannot have access level SECURITY.");
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
        validate(toUpdate);
    }

    @Transactional(readOnly = true)
    @Override
    public List<? extends Asset<?>> findAssetsByAgentWithSecurityAccess(@Nonnull AccessControlAgent agent) {
        return dao.findAssetsByAgentWithSecurityAccess(agent);
    }
}
