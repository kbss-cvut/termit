/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If not, see
 * <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.termit.dto.RecentlyCommentedAsset;
import cz.cvut.kbss.termit.dto.RecentlyModifiedAsset;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.dao.AssetDao;
import cz.cvut.kbss.termit.service.repository.TermRepositoryService;
import cz.cvut.kbss.termit.service.security.SecurityUtils;
import cz.cvut.kbss.termit.service.security.authorization.VocabularyAuthorizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class AssetService {

    /**
     * Mask used to sanitize strings the current user is unauthorized to read.
     */
    static final String MASK = "**********";

    private final TermRepositoryService termRepositoryService;

    private final AssetDao assetDao;

    private final VocabularyAuthorizationService vocabularyAuthorizationService;

    @Autowired
    public AssetService(TermRepositoryService termRepositoryService, AssetDao assetDao,
                        VocabularyAuthorizationService vocabularyAuthorizationService) {
        this.termRepositoryService = termRepositoryService;
        this.assetDao = assetDao;
        this.vocabularyAuthorizationService = vocabularyAuthorizationService;
    }

    /**
     * Finds the specified number of most recently added/edited assets.
     *
     * @param pageSpec Specification of the page to load
     * @return Page of recently added/edited assets
     */
    public Page<RecentlyModifiedAsset> findLastEdited(Pageable pageSpec) {
        return sanitizeUnauthorizedAssets(assetDao.findLastEdited(pageSpec));
    }

    /**
     * Sanitizes elements of the provided input if the current user has no read access to them.
     * <p>
     * To preserve consistent paging functionality, this method does not remove the elements, only scrambles their
     * label. If the user attempts to access them, they will get a forbidden response (secured by the corresponding
     * authorization services).
     *
     * @param input Input elements to sanitize
     * @return Sanitized input
     */
    private Page<RecentlyModifiedAsset> sanitizeUnauthorizedAssets(Page<RecentlyModifiedAsset> input) {
        input.get().filter(ra -> !vocabularyAuthorizationService.canRead(
                     new Vocabulary(ra.getVocabulary() != null ? ra.getVocabulary() : ra.getUri())))
             .forEach(ra -> ra.setLabel(MASK));
        return input;
    }

    /**
     * Finds the specified number of most recently commented assets.
     *
     * @param pageSpec Specification of the result to return
     * @return Page of recently commented assets
     */
    public Page<RecentlyCommentedAsset> findLastCommented(Pageable pageSpec) {
        return sanitizeUnauthorizedComments(termRepositoryService.findLastCommented(pageSpec));
    }

    /**
     * Sanitizes elements of the provided input if the current user has no read access to them.
     * <p>
     * To preserve consistent paging functionality, this method does not remove the elements, only scrambles the content
     * of the comment. If the user attempts to access the asset, they will get a forbidden response (secured by the
     * corresponding authorization services).
     *
     * @param input Input elements to sanitize
     * @return Sanitized input
     */
    private Page<RecentlyCommentedAsset> sanitizeUnauthorizedComments(Page<RecentlyCommentedAsset> input) {
        input.get().filter(ra -> !vocabularyAuthorizationService.canRead(
                     new Vocabulary(ra.getVocabulary() != null ? ra.getVocabulary() : ra.getUri())))
             .forEach(ra -> {
                 ra.setLabel(MASK);
                 ra.getLastComment().setContent(MASK);
             });
        return input;
    }

    /**
     * Finds the specified number of the current user's most recently added/edited assets.
     *
     * @param pageSpec Specification of the page to load
     * @return Page of recently added/edited assets
     */
    public Page<RecentlyModifiedAsset> findMyLastEdited(Pageable pageSpec) {
        final User me = SecurityUtils.currentUser().toUser();
        return assetDao.findLastEditedBy(me, pageSpec);
    }

    /**
     * Finds the specified number of assets last commented in reaction to the current user' comments.
     *
     * @param pageSpec Specification of the size and number of page to return
     * @return List of recently commented assets
     */
    public Page<RecentlyCommentedAsset> findLastCommentedInReactionToMine(Pageable pageSpec) {
        final User me = SecurityUtils.currentUser().toUser();
        return termRepositoryService.findLastCommentedInReaction(me, pageSpec);
    }

    /**
     * Finds the specified number of my assets last commented.
     *
     * @param pageSpec Specification of the page to return
     * @return List of recently commented assets
     */
    public Page<RecentlyCommentedAsset> findMyLastCommented(Pageable pageSpec) {
        final User me = SecurityUtils.currentUser().toUser();
        return termRepositoryService.findMyLastCommented(me, pageSpec);
    }
}
