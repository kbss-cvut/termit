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
package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.termit.dto.RdfsResource;
import cz.cvut.kbss.termit.persistence.dao.DataDao;
import jakarta.annotation.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;
import java.util.Optional;

@Service
public class DataRepositoryService {

    private final DataDao dataDao;

    @Autowired
    public DataRepositoryService(DataDao dataDao) {
        this.dataDao = dataDao;
    }

    /**
     * Gets all properties present in the system.
     *
     * @return List of properties, ordered by label
     */
    public List<RdfsResource> findAllProperties() {
        return dataDao.findAllProperties();
    }

    /**
     * Gets basic metadata about a resource with the specified identifier.
     *
     * @param id Resource identifier
     * @return Wrapped matching resource or an empty {@code Optional} if no such resource exists
     */
    public Optional<RdfsResource> find(URI id) {
        return dataDao.find(id);
    }

    /**
     * Persists the specified property.
     * <p>
     *
     * @param property The property to persist
     */
    @Transactional
    public void persistProperty(RdfsResource property) {
        dataDao.persist(property);
    }

    /**
     * Gets the label of a resource with the specified identifier.
     *
     * @param id Resource identifier
     * @param language Label language, if null, configured persistence unit language is used instead
     * @return Matching resource identifier (if found)
     */
    public Optional<String> getLabel(URI id, @Nullable String language) {
        return dataDao.getLabel(id, language);
    }
}
