package cz.cvut.kbss.termit.service.security.authorization;

import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.service.security.SecurityUtils;
import cz.cvut.kbss.termit.workspace.EditableVocabularies;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Authorizes access to vocabularies.
 */
@Service
public class VocabularyAuthorizationService implements AssetAuthorizationService<Vocabulary> {

    private final EditableVocabularies editableVocabularies;

    public VocabularyAuthorizationService(EditableVocabularies editableVocabularies) {
        this.editableVocabularies = editableVocabularies;
    }

    private boolean isUserAtLeastEditor() {
        final UserAccount user = SecurityUtils.currentUser();
        return user.isAdmin() || user.hasType(cz.cvut.kbss.termit.util.Vocabulary.s_c_plny_uzivatel_termitu);
    }

    @Override
    public boolean canView(Vocabulary asset) {
        return true;
    }

    @Override
    public boolean canRead(Vocabulary asset) {
        return true;
    }

    @Override
    public boolean canModify(Vocabulary asset) {
        Objects.requireNonNull(asset);
        // Currently just check workspace. in the future, this will also be checking ACL of the vocabulary w.r.t. the
        // current user
        return isUserAtLeastEditor() && editableVocabularies.isEditable(asset);
    }

    @Override
    public boolean canRemove(Vocabulary asset) {
        return isUserAtLeastEditor() && editableVocabularies.isEditable(asset);
    }
}
