package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.termit.dto.acl.AccessControlListDto;
import cz.cvut.kbss.termit.model.AccessControlAgent;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.acl.AccessControlList;
import cz.cvut.kbss.termit.model.acl.AccessControlRecord;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import org.springframework.lang.NonNull;

import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing {@link AccessControlList}s (ACLs).
 * <p>
 * Note that only management of ACLs is supported by this service. Access control itself is handled by TODO.
 */
public interface AccessControlListService {

    /**
     * Finds an {@link AccessControlList} with the specified identifier.
     *
     * @param id ACL identifier
     * @return Matching ACL
     * @throws cz.cvut.kbss.termit.exception.NotFoundException If no matching ACL exists
     */
    AccessControlList findRequired(URI id);

    /**
     * Gets a reference to an {@link AccessControlList} with the specified identifier.
     *
     * @param id ACL identifier
     * @return Reference to a matching ACL
     * @throws cz.cvut.kbss.termit.exception.NotFoundException If no matching ACL exists
     */
    AccessControlList getRequiredReference(URI id);

    /**
     * Finds an {@link AccessControlList} guarding access to the specified subject.
     *
     * @param subject Subject of the ACL
     * @return Matching ACL instance wrapped in an {@link Optional}, empty {@link Optional} if no such ACL exists
     * @see #findForAsDto(HasIdentifier)
     */
    Optional<AccessControlList> findFor(HasIdentifier subject);

    /**
     * Finds an {@link AccessControlList} guarding access to the specified subject and returns it as DTO.
     *
     * @param subject Subject of the ACL
     * @return Matching ACL instance DTO wrapped in an {@link Optional}, empty {@link Optional} if no such ACL exists
     * @see #findFor(HasIdentifier)
     */
    Optional<AccessControlListDto> findForAsDto(HasIdentifier subject);

    /**
     * Creates and persists an {@link AccessControlList} with default access records and returns it.
     * <p>
     * The created ACL has the following records:
     * <ul>
     *     <li>{@link cz.cvut.kbss.termit.model.acl.AccessLevel#SECURITY} for the current user (if available)</li>
     *     <li>{@link cz.cvut.kbss.termit.model.acl.AccessLevel#SECURITY} for all authors of the specified subject (if {@link cz.cvut.kbss.termit.model.changetracking.PersistChangeRecord}s exist for the specified asset)</li>
     *     <li>Access level for {@link cz.cvut.kbss.termit.security.SecurityConstants#ROLE_FULL_USER} and {@link cz.cvut.kbss.termit.security.SecurityConstants#ROLE_RESTRICTED_USER} based on configuration</li>
     * </ul>
     *
     * @return New ACL with default records
     */
    AccessControlList createFor(HasIdentifier subject);

    /**
     * Removes the specified access control list and its records.
     *
     * @param acl The ACL to remove
     */
    void remove(AccessControlList acl);

    /**
     * Creates a new {@link AccessControlList} by cloning the specified one.
     * <p>
     * The new ACL thus contains records for the same holders as the original and with the same access levels.
     *
     * @param original ACL to clone
     * @return The new ACL
     */
    AccessControlList clone(AccessControlList original);

    /**
     * Adds the specified record to the specified target {@link AccessControlList}.
     *
     * @param acl    Target ACL
     * @param record Record to add to the ACL
     */
    void addRecord(AccessControlList acl, AccessControlRecord<?> record);

    /**
     * Removes the specified record from the specified target {@link AccessControlList}.
     *
     * @param acl    Target ACL
     * @param record Record to remove from the ACL
     */
    void removeRecord(AccessControlList acl, AccessControlRecord<?> record);

    /**
     * Updates access level in specified record in the specified target {@link AccessControlList}.
     * <p>
     * Only access level can be changed in an existing {@link AccessControlRecord}. Changing holder requires creating a
     * record.
     *
     * @param acl    Target ACL
     * @param record Updated access control record
     */
    void updateRecordAccessLevel(AccessControlList acl, AccessControlRecord<?> record);

    /**
     * Gets a list of assets to which the specified agent has security-level access ({@link
     * cz.cvut.kbss.termit.model.acl.AccessLevel#SECURITY}).
     *
     * @param agent Agent whose access to examine
     * @return List of matching assets
     */
    List<? extends Asset<?>> findAssetsByAgentWithSecurityAccess(@NonNull AccessControlAgent agent);
}
