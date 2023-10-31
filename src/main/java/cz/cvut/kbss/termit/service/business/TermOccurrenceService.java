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
package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.termit.model.assignment.TermOccurrence;

import java.net.URI;

/**
 * Business service for managing {@link TermOccurrence}s.
 */
public interface TermOccurrenceService {

    /**
     * Gets a reference to a {@link TermOccurrence} with the specified identifier.
     * <p>
     * The returned instance may be empty apart from its identifier.
     *
     * @param id Term occurrence identifier
     * @return Matching term occurrence
     * @throws cz.cvut.kbss.termit.exception.NotFoundException If there is no such term occurrence
     */
    TermOccurrence getRequiredReference(URI id);

    /**
     * Persists the specified term occurrence.
     *
     * @param occurrence Occurrence to persist
     */
    void persist(TermOccurrence occurrence);

    /**
     * Approves the specified term occurrence.
     * <p>
     * This removes the suggested classification of the occurrence if it were present.
     *
     * @param occurrence Occurrence to approve
     */
    void approve(TermOccurrence occurrence);

    /**
     * Removes the specified term occurrence.
     *
     * @param occurrence Occurrence to remove
     */
    void remove(TermOccurrence occurrence);
}
