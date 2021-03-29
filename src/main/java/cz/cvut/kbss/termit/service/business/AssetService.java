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
import cz.cvut.kbss.termit.service.repository.ResourceRepositoryService;
import cz.cvut.kbss.termit.service.repository.TermRepositoryService;
import cz.cvut.kbss.termit.service.repository.VocabularyRepositoryService;
import cz.cvut.kbss.termit.service.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class AssetService {

    private final ResourceRepositoryService resourceRepositoryService;

    private final TermRepositoryService termRepositoryService;

    private final VocabularyRepositoryService vocabularyRepositoryService;

    private final SecurityUtils securityUtils;

    @Autowired
    public AssetService(ResourceRepositoryService resourceRepositoryService,
                        TermRepositoryService termRepositoryService,
                        VocabularyRepositoryService vocabularyRepositoryService,
                        SecurityUtils securityUtils) {
        this.resourceRepositoryService = resourceRepositoryService;
        this.termRepositoryService = termRepositoryService;
        this.vocabularyRepositoryService = vocabularyRepositoryService;
        this.securityUtils = securityUtils;
    }

    /**
     * Finds the specified number of most recently added/edited assets.
     *
     * @param limit Maximum number of assets to retrieve
     * @return List of recently added/edited assets
     */
    public List<RecentlyModifiedAsset> findLastEdited(int limit) {
        ensureValidLimitForLastEdited(limit);
        final List<RecentlyModifiedAsset> resources = resourceRepositoryService.findLastEdited(limit);
        final List<RecentlyModifiedAsset> terms = termRepositoryService.findLastEdited(limit);
        final List<RecentlyModifiedAsset> vocabularies = vocabularyRepositoryService.findLastEdited(limit);
        final List<RecentlyModifiedAsset> result = mergeAssets(mergeAssets(resources, terms), vocabularies);
        return result.subList(0, Math.min(result.size(), limit));
    }

    /**
     * Finds the specified number of most recently commented assets.
     *
     * @param limit Maximum number of assets to retrieve
     * @return List of recently commented assets
     */
    public List<RecentlyCommentedAsset> findLastCommented(int limit) {
        ensureValidLimitForLastCommented(limit);
        final List<RecentlyCommentedAsset> result = termRepositoryService.findLastCommented(limit);
        return result.subList(0, Math.min(result.size(), limit));
    }

    private static List<RecentlyModifiedAsset> mergeAssets(List<RecentlyModifiedAsset> listOne,
                                                           List<RecentlyModifiedAsset> listTwo) {
        int oneIndex = 0;
        int twoIndex = 0;
        final List<RecentlyModifiedAsset> result = new ArrayList<>(listOne.size() + listTwo.size());
        while (oneIndex < listOne.size() && twoIndex < listTwo.size()) {
            if (listOne.get(oneIndex).getModified()
                       .compareTo(listTwo.get(twoIndex).getModified()) >= 0) {
                result.add(listOne.get(oneIndex));
                oneIndex++;
            } else {
                result.add(listTwo.get(twoIndex));
                twoIndex++;
            }
        }
        addRest(result, listOne, oneIndex);
        addRest(result, listTwo, twoIndex);
        return result;
    }

    private static void addRest(List<RecentlyModifiedAsset> target, List<RecentlyModifiedAsset> source, int index) {
        while (index < source.size()) {
            target.add(source.get(index++));
        }
    }

    /**
     * Finds the specified number of the current user's most recently added/edited assets.
     *
     * @param limit Maximum number of assets to retrieve
     * @return List of recently added/edited assets
     */
    public List<RecentlyModifiedAsset> findMyLastEdited(int limit) {
        ensureValidLimitForLastEdited(limit);
        final User me = securityUtils.getCurrentUser().toUser();
        final List<RecentlyModifiedAsset> resources = resourceRepositoryService.findLastEditedBy(me, limit);
        final List<RecentlyModifiedAsset> terms = termRepositoryService.findLastEditedBy(me, limit);
        final List<RecentlyModifiedAsset> vocabularies = vocabularyRepositoryService.findLastEditedBy(me, limit);
        final List<RecentlyModifiedAsset> result = mergeAssets(mergeAssets(resources, terms), vocabularies);
        return result.subList(0, Math.min(result.size(), limit));
    }

    private void ensureValidLimitForLastEdited(int limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("Maximum for recently edited assets must not be less than 0.");
        }
    }

    /**
     * Finds the specified number of assets last commented in reaction to the current user' comments.
     *
     * @param limit Maximum number of assets to retrieve
     * @return List of recently commented assets
     */
    public List<RecentlyCommentedAsset> findLastCommentedInReactionToMine(int limit) {
        ensureValidLimitForLastCommented(limit);
        final User me = securityUtils.getCurrentUser().toUser();
        final List<RecentlyCommentedAsset> result = termRepositoryService.findLastCommentedInReaction(me, limit);
        return result.subList(0, Math.min(result.size(), limit));
    }

    /**
     * Finds the specified number of my assets last commented.
     *
     * @param limit Maximum number of assets to retrieve
     * @return List of recently commented assets
     */
    public List<RecentlyCommentedAsset> findMyLastCommented(int limit) {
        ensureValidLimitForLastCommented(limit);
        final User me = securityUtils.getCurrentUser().toUser();
        final List<RecentlyCommentedAsset> result = termRepositoryService.findMyLastCommented(me, limit);
        return result.subList(0, Math.min(result.size(), limit));
    }

    /**
     * Finds the specified number of assets most recently commented by me.
     *
     * @param limit Maximum number of assets to retrieve
     * @return List of recently commented assets
     */
    public List<RecentlyCommentedAsset> findLastCommentedByMe(int limit) {
        ensureValidLimitForLastCommented(limit);
        final User me = securityUtils.getCurrentUser().toUser();
        final List<RecentlyCommentedAsset> result = termRepositoryService.findLastCommentedByMe(me, limit);
        return result.subList(0, Math.min(result.size(), limit));
    }

    private void ensureValidLimitForLastCommented(int limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("Maximum for recently commented assets must not be less than 0.");
        }
    }
}
