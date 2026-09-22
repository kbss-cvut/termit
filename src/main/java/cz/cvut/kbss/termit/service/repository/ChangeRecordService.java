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
package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.termit.dto.filter.ChangeRecordFilterDto;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.changetracking.UpdateChangeRecord;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.persistence.dao.changetracking.ChangeRecordDao;
import cz.cvut.kbss.termit.service.changetracking.ChangeRecordProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class ChangeRecordService implements ChangeRecordProvider<Asset<?>> {

    private final ChangeRecordDao changeRecordDao;

    @Autowired
    public ChangeRecordService(ChangeRecordDao changeRecordDao) {
        this.changeRecordDao = changeRecordDao;
    }

    @Override
    public List<AbstractChangeRecord> getChanges(Asset<?> asset, ChangeRecordFilterDto filterDto) {
        return changeRecordDao.findAll(asset, filterDto);
    }

    /**
     * Gets authors of the specified asset.
     * <p>
     * This method returns a collection because some assets may have multiple authors (e.g., when a vocabulary is
     * re-imported from SKOS by a different user). Also, some assets may not have recorded authors (e.g., terms from an
     * imported vocabulary), in which case the result of this method is empty.
     *
     * @param asset Asset whose authors to get
     * @return A set of zero or more authors of the specified asset
     */
    public Set<User> getAuthors(HasIdentifier asset) {
        return changeRecordDao.getAuthors(asset);
    }

    /**
     * Finds an update change record with the specified identifier.
     *
     * @param recordUri Change record identifier
     * @return Matching update change record
     * @throws NotFoundException If no matching record is found
     */
    public UpdateChangeRecord findUpdateRequired(URI recordUri) {
        return findRequired(UpdateChangeRecord.class, recordUri);
    }

    /**
     * Finds a change record of the specified type and identifier.
     *
     * @param recordClass Change record type
     * @param recordUri   Change record identifier
     * @return Matching change record
     * @throws NotFoundException If no matching record is found
     */
    private <T extends AbstractChangeRecord> T findRequired(Class<T> recordClass, URI recordUri) {
        return find(recordClass, recordUri)
                .orElseThrow(()-> new NotFoundException(recordClass.getSimpleName() + " not found"));
    }

    /**
     * Finds a change record of the specified type and identifier.
     *
     * @param recordClass Change record type
     * @param recordUri   Change record identifier
     * @return Matching record, or an empty optional if none is found
     */
    private <T extends AbstractChangeRecord> Optional<T> find(Class<T> recordClass, URI recordUri) {
        return changeRecordDao.find(recordClass, recordUri);
    }
}
