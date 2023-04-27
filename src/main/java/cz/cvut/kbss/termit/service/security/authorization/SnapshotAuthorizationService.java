package cz.cvut.kbss.termit.service.security.authorization;

import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.security.model.UserRole;
import cz.cvut.kbss.termit.service.security.SecurityUtils;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Validates snapshot management actions.
 * <p>
 * Note that creation of snapshots is authorized by the services relevant to the assets being snapshot (e.g., {@link
 * VocabularyAuthorizationService} for {@link cz.cvut.kbss.termit.model.Vocabulary} instances).
 */
@Service
public class SnapshotAuthorizationService {

    private final VocabularyAuthorizationService vocabularyAuthorizationService;

    public SnapshotAuthorizationService(VocabularyAuthorizationService vocabularyAuthorizationService) {
        this.vocabularyAuthorizationService = vocabularyAuthorizationService;
    }

    /**
     * Checks if the current user is authorized to remove the specified snapshot.
     *
     * @param snapshot Snapshot to remove
     * @return {@code true} if the current user can remove the snapshot, {@code false} otherwise
     */
    public boolean canRemove(Snapshot snapshot) {
        Objects.requireNonNull(snapshot);
        if (snapshot.hasType(cz.cvut.kbss.termit.util.Vocabulary.s_c_verze_slovniku)) {
            final Vocabulary vocabulary = new Vocabulary(snapshot.getVersionOf());
            return vocabularyAuthorizationService.canRemoveSnapshot(vocabulary);
        }
        return isUserAtLeastEditor();
    }

    private boolean isUserAtLeastEditor() {
        final UserAccount user = SecurityUtils.currentUser();
        return user.isAdmin() || user.hasRole(UserRole.FULL_USER);
    }
}
