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
import cz.cvut.kbss.termit.exception.ResourceExistsException;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.persistence.dao.BaseAssetDao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import jakarta.validation.Validator;

/**
 * Base repository service implementation for asset managing services.
 *
 * @param <T> Asset type
 */
public abstract class BaseAssetRepositoryService<T extends Asset<?>, DTO extends HasIdentifier> extends BaseRepositoryService<T, DTO> {

    protected BaseAssetRepositoryService(Validator validator) {
        super(validator);
    }

    @Override
    protected abstract BaseAssetDao<T> getPrimaryDao();

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
     * @param pageSpec Specification of the size and number of page to return
     * @return List of most recently commented assets
     */
    public Page<RecentlyCommentedAsset> findLastCommented(Pageable pageSpec) {
        return getPrimaryDao().findLastCommented(pageSpec);
    }

    /**
     * Gets the specified number of assets last commented in reaction to the given users' comments .
     * <p>
     * The returned assets are sorted by commented date in descending order.
     *
     * @param pageSpec Specification of the size and number of page to return
     * @return List of most recently commented assets
     */
    public Page<RecentlyCommentedAsset> findLastCommentedInReaction(User me, Pageable pageSpec) {
        return getPrimaryDao().findLastCommentedInReaction(me, pageSpec);
    }

    /**
     * Gets the specified number of my last commented assets.
     * <p>
     * The returned assets are sorted by commented date in descending order.
     *
     * @param pageSpec Specification of the page to return
     * @return List of most recently commented assets
     */
    public Page<RecentlyCommentedAsset> findMyLastCommented(User me, Pageable pageSpec) {
        return getPrimaryDao().findMyLastCommented(me, pageSpec);
    }
}
