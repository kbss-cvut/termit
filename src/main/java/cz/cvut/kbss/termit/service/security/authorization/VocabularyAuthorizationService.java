package cz.cvut.kbss.termit.service.security.authorization;

import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.security.model.UserRole;
import cz.cvut.kbss.termit.service.security.SecurityUtils;
import cz.cvut.kbss.termit.service.security.authorization.acl.AccessControlListBasedAuthorizationService;
import cz.cvut.kbss.termit.workspace.EditableVocabularies;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Authorizes access to vocabularies.
 */
@Service
public class VocabularyAuthorizationService implements AssetAuthorizationService<Vocabulary> {

    private final AccessControlListBasedAuthorizationService aclAuthorizationService;

    private final EditableVocabularies editableVocabularies;

    public VocabularyAuthorizationService(AccessControlListBasedAuthorizationService aclAuthorizationService,
                                          EditableVocabularies editableVocabularies) {
        this.aclAuthorizationService = aclAuthorizationService;
        this.editableVocabularies = editableVocabularies;
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

    @Override
    public boolean canRead(Vocabulary asset) {
        Objects.requireNonNull(asset);
        final UserAccount user = SecurityUtils.currentUser();
        return aclAuthorizationService.canRead(user, asset) && editableVocabularies.isEditable(asset);
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
}
