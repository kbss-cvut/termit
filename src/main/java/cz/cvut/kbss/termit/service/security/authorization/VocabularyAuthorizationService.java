package cz.cvut.kbss.termit.service.security.authorization;

import cz.cvut.kbss.termit.dto.listing.VocabularyDto;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.acl.AccessLevel;
import cz.cvut.kbss.termit.security.model.UserRole;
import cz.cvut.kbss.termit.service.repository.VocabularyRepositoryService;
import cz.cvut.kbss.termit.service.security.SecurityUtils;
import cz.cvut.kbss.termit.service.security.authorization.acl.AccessControlListBasedAuthorizationService;
import cz.cvut.kbss.termit.workspace.EditableVocabularies;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Objects;

/**
 * Authorizes access to vocabularies.
 */
@Service
public class VocabularyAuthorizationService implements AssetAuthorizationService<Vocabulary> {

    private final AccessControlListBasedAuthorizationService aclAuthorizationService;

    private final EditableVocabularies editableVocabularies;

    private final VocabularyRepositoryService vocabularyRepositoryService;

    public VocabularyAuthorizationService(AccessControlListBasedAuthorizationService aclAuthorizationService,
                                          EditableVocabularies editableVocabularies,
                                          VocabularyRepositoryService vocabularyRepositoryService) {
        this.aclAuthorizationService = aclAuthorizationService;
        this.editableVocabularies = editableVocabularies;
        this.vocabularyRepositoryService = vocabularyRepositoryService;
    }

    /**
     * Checks if the current user can create a vocabulary.
     * <p>
     * Currently, this check means that a user must be at least in the editor role.
     *
     * @return {@code true} if the current user can create a vocabulary, {@code false} otherwise
     */
    public boolean canCreate() {
        return isUserAtLeastEditor();
    }

    private boolean isUserAtLeastEditor() {
        final UserAccount user = SecurityUtils.currentUser();
        return user.isAdmin() || user.hasRole(UserRole.FULL_USER);
    }

    /**
     * Checks if the current user can create a snapshot of the specified vocabulary.
     *
     * @param asset Vocabulary whose snapshot is to be created
     * @return {@code true} if the current user can create the snapshot, {@code false} otherwise
     */
    public boolean canCreateSnapshot(Vocabulary asset) {
        Objects.requireNonNull(asset);
        final UserAccount user = SecurityUtils.currentUser();
        return aclAuthorizationService.hasAccessLevel(AccessLevel.SECURITY, user, asset) && editableVocabularies.isEditable(asset);
    }

    /**
     * Checks if the current user can remove a snapshot of the specified vocabulary.
     * <p>
     * Snapshot removal is authorized based on the target vocabulary, not on the individual snapshots.
     *
     * @param asset Vocabulary whose snapshot is to be removed
     * @return {@code true} if the current user can remove the vocabulary's snapshots, {@code false} otherwise
     */
    public boolean canRemoveSnapshot(Vocabulary asset) {
        Objects.requireNonNull(asset);
        final UserAccount user = SecurityUtils.currentUser();
        return aclAuthorizationService.hasAccessLevel(AccessLevel.SECURITY, user, asset) && editableVocabularies.isEditable(asset);
    }

    /**
     * Checks if the current user can manage access to the specified vocabulary.
     * <p>
     * A use can manage access to a vocabulary when they have {@link AccessLevel#SECURITY} level access.
     *
     * @param asset Vocabulary to which access is checked
     * @return {@code true} if the current user can manage the ACL of the specified vocabulary, {@code false} otherwise
     */
    public boolean canManageAccess(Vocabulary asset) {
        Objects.requireNonNull(asset);
        final UserAccount user = SecurityUtils.currentUser();
        return aclAuthorizationService.hasAccessLevel(AccessLevel.SECURITY, user, asset);
    }

    /**
     * Checks if the current use can remove files from a document associated with the specified vocabulary.
     * <p>
     * A user can remove files from a vocabulary document when they have {@link AccessLevel#SECURITY} level access to
     * the vocabulary.
     *
     * @param asset Vocabulary to which access is checked
     * @return {@code true} if the current user can remove files from the specified vocabulary's document, {@code false}
     * otherwise
     */
    public boolean canRemoveFiles(Vocabulary asset) {
        Objects.requireNonNull(asset);
        final UserAccount user = SecurityUtils.currentUser();
        return aclAuthorizationService.hasAccessLevel(AccessLevel.SECURITY, user, asset);
    }

    /**
     * Checks if the current user can reimport a vocabulary with the specified identifier.
     *
     * @param vocabularyIri Vocabulary identifier
     * @return {@code true} if the current user can reimport the vocabulary or no such vocabulary exists and the user is
     * authorized to create a new vocabulary, {@code false} otherwise
     */
    public boolean canReimport(URI vocabularyIri) {
        final UserAccount user = SecurityUtils.currentUser();
        if (vocabularyRepositoryService.exists(vocabularyIri)) {
            final Vocabulary voc = new Vocabulary(vocabularyIri);
            return aclAuthorizationService.hasAccessLevel(AccessLevel.SECURITY, user, new Vocabulary(
                    vocabularyIri)) && editableVocabularies.isEditable(voc);
        }
        return canCreate();
    }

    @Override
    public boolean canRead(Vocabulary asset) {
        Objects.requireNonNull(asset);
        final UserAccount user = SecurityUtils.currentUser();
        return aclAuthorizationService.canRead(user, asset) && editableVocabularies.isEditable(asset);
    }

    public boolean canRead(VocabularyDto dto) {
        Objects.requireNonNull(dto);
        return canRead(new Vocabulary(dto.getUri()));
    }

    @Override
    public boolean canModify(Vocabulary asset) {
        Objects.requireNonNull(asset);
        final UserAccount user = SecurityUtils.currentUser();
        return aclAuthorizationService.canModify(user, asset) && editableVocabularies.isEditable(asset);
    }

    @Override
    public boolean canRemove(Vocabulary asset) {
        Objects.requireNonNull(asset);
        final UserAccount user = SecurityUtils.currentUser();
        return aclAuthorizationService.canRemove(user, asset) && editableVocabularies.isEditable(asset);
    }

    /**
     * Gets the level of access the current user has to the specified vocabulary.
     *
     * @param asset Vocabulary access to which is to be determined
     * @return Access level of the current user
     */
    public AccessLevel getAccessLevel(Vocabulary asset) {
        Objects.requireNonNull(asset);
        final UserAccount user = SecurityUtils.currentUser();
        return editableVocabularies.isEditable(asset) ? aclAuthorizationService.getAccessLevel(user, asset) : AccessLevel.NONE;
    }
}
