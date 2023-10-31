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
package cz.cvut.kbss.termit.service.changetracking;

import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.util.HasIdentifier;

import java.util.List;

/**
 * Service which can provide change records for assets.
 *
 * @param <T> Type of asset to get changes for
 */
public interface ChangeRecordProvider<T extends HasIdentifier> {

    /**
     * Gets change records of the specified asset.
     *
     * @param asset Asset to find change records for
     * @return List of change records, ordered by record timestamp in descending order
     */
    List<AbstractChangeRecord> getChanges(T asset);
}
