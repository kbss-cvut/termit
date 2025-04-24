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
package cz.cvut.kbss.termit.service.business;

import java.net.URI;
import java.util.List;

/**
 * Declares Create, Retrieve, Update and Delete (CRUD) operations for business services.
 *
 * @param <T> Type of the concept managed by this service
 * @param <DTO> Type of DTO used by the findAll retrieval method
 */
public interface CrudService<T, DTO> extends RudService<T> {

    /**
     * Gets all items of the type managed by this service from the repository.
     *
     * @return List of items
     */
    List<DTO> findAll();

    /**
     * Checks if an item with the specified identifier exists.
     *
     * @param id Item identifier
     * @return Existence check result
     */
    default boolean exists(URI id) {
        return find(id).isPresent();
    }

    /**
     * Persists the specified item.
     *
     * @param instance Item to save
     */
    void persist(T instance);

}
