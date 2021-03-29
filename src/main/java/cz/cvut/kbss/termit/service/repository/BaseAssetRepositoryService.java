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
package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.termit.dto.RecentlyCommentedAsset;
import cz.cvut.kbss.termit.dto.RecentlyModifiedAsset;
import cz.cvut.kbss.termit.exception.ResourceExistsException;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.persistence.dao.AssetDao;

import javax.validation.Validator;
import java.util.List;

/**
 * Base repository service implementation for asset managing services.
 *
 * @param <T> Asset type
 */
public abstract class BaseAssetRepositoryService<T extends Asset<?>> extends BaseRepositoryService<T> {

    protected BaseAssetRepositoryService(Validator validator) {
        super(validator);
    }

    @Override
    protected abstract AssetDao<T> getPrimaryDao();

    /**
     * Gets the specified number of recently added/edited assets.
     * <p>
     * The returned assets are sorted by added/edited date in descending order.
     *
     * @param limit Maximum number of assets returned
     * @return List of most recently added/edited assets
     */
    public List<RecentlyModifiedAsset> findLastEdited(int limit) {
        return getPrimaryDao().findLastEdited(limit);
    }

    /**
     * Gets the specified number of the specified user's recently added/edited assets.
     * <p>
     * By <i>"the specified user's"</i> it is meant that the user has edited or created the assets.
     * <p>
     * The returned assets are sorted by added/edited date in descending order.
     *
     * @param limit Maximum number of assets returned
     * @return List of most recently added/edited assets
     */
    public List<RecentlyModifiedAsset> findLastEditedBy(User user, int limit) {
        return getPrimaryDao().findLastEditedBy(user, limit);
    }

    protected void verifyIdentifierUnique(T instance) {
        if (exists(instance.getUri())) {
            throw ResourceExistsException.create(instance.getClass().getSimpleName(), instance.getUri());
        }
    }

    /**
     * Gets the specified number of recently commented assets.
     * <p>
     * The returned assets are sorted by commented date in descending order.
     *
     * @param limit Maximum number of assets returned
     * @return List of most recently commented assets
     */
    public List<RecentlyCommentedAsset> findLastCommented(int limit) {
        return getPrimaryDao().findLastCommented(limit);
    }

    /**
     * Gets the specified number of assets last commented in reaction to the given users' comments .
     * <p>
     * The returned assets are sorted by commented date in descending order.
     *
     * @param limit Maximum number of assets returned
     * @return List of most recently commented assets
     */
    public List<RecentlyCommentedAsset> findLastCommentedInReaction(User me, int limit) {
        return getPrimaryDao().findLastCommentedInReaction(me, limit);
    }

    /**
     * Gets the specified number of my last commented assets.
     * <p>
     * The returned assets are sorted by commented date in descending order.
     *
     * @param limit Maximum number of assets returned
     * @return List of most recently commented assets
     */
    public List<RecentlyCommentedAsset> findMyLastCommented(User me, int limit) {
        return getPrimaryDao().findMyLastCommented(me, limit);
    }

    /**
     * Gets the specified number of assets last commented by me.
     * <p>
     * The returned assets are sorted by commented date in descending order.
     *
     * @param limit Maximum number of assets returned
     * @return List of most recently commented assets
     */
    public List<RecentlyCommentedAsset> findLastCommentedByMe(User me, int limit) {
        return getPrimaryDao().findLastCommentedByMe(me, limit);
    }
}
