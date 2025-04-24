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
package cz.cvut.kbss.termit.service.changetracking;

import cz.cvut.kbss.termit.dto.filter.ChangeRecordFilterDto;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;

import java.util.List;

/**
 * Service which can provide change records for assets.
 *
 * @param <T> Type of asset to get changes for
 */
public interface ChangeRecordProvider<T extends Asset<?>> {

    /**
     * Gets change records of the specified asset
     * filtered by {@link ChangeRecordFilterDto}.
     *
     * @param asset Asset to find change records for
     * @param filterDto Filter parameters
     * @return List of change records, ordered by record timestamp in descending order
     */
    List<AbstractChangeRecord> getChanges(T asset, ChangeRecordFilterDto filterDto);

    /**
     * Gets change records of the specified asset.
     *
     * @param asset Asset to find change records for
     * @return List of change records, ordered by record timestamp in descending order
     */
    default List<AbstractChangeRecord> getChanges(T asset) {
        return getChanges(asset, new ChangeRecordFilterDto());
    }
}
