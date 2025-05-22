/*
 * TermIt
 * Copyright (C) 2025 Czech Technical University in Prague
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
package cz.cvut.kbss.termit.persistence.dao.changetracking;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.Asset;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.Objects;

@Repository
public class ChangeTrackingHelperDao {

    private final EntityManager em;

    @Autowired
    public ChangeTrackingHelperDao(EntityManager em) {
        this.em = em;
    }

    /**
     * Finds an existing stored instance of the specified asset.
     *
     * @param update Current state of the asset to find
     * @return Stored state of the searched asset
     */
    public <T extends Asset<?>> T findStored(T update) {
        Objects.requireNonNull(update);
        final T result = (T) em.find(update.getClass(), update.getUri());
        if (result == null) {
            throw NotFoundException.create(update.getClass().getSimpleName(), update.getUri());
        }
        // We do not want the result to be in the persistence context when updates happen later (mainly to prevent issues with repository contexts)
        em.detach(result);
        return result;
    }
}
