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

    private final SecurityUtils securityUtils;

    public SnapshotAuthorizationService(VocabularyAuthorizationService vocabularyAuthorizationService,
                                        SecurityUtils securityUtils) {
        this.vocabularyAuthorizationService = vocabularyAuthorizationService;
        this.securityUtils = securityUtils;
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
        final UserAccount user = securityUtils.getCurrentUser();
        return user.isAdmin() || user.hasRole(UserRole.FULL_USER);
    }
}
