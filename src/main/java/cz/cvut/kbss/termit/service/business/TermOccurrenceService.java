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

import cz.cvut.kbss.termit.dto.assignment.TermOccurrences;
import cz.cvut.kbss.termit.model.AbstractTerm;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.assignment.TermOccurrence;

import java.net.URI;
import java.util.List;

/**
 * Business service for managing {@link TermOccurrence}s.
 */
public interface TermOccurrenceService {

    /**
     * Persists the specified term occurrence.
     *
     * @param occurrence Occurrence to persist
     */
    void persist(TermOccurrence occurrence);

    /**
     * Saves the specified term occurrence, either persisting it or updating if it already exists.
     * <p>
     * If the occurrence already exists, it is assumed that the term has changed and only this attribute is updated.
     *
     * @param occurrence Occurrence to save
     */
    void persistOrUpdate(TermOccurrence occurrence);

    /**
     * Approves term occurrence with the specified identifier.
     * <p>
     * This removes the suggested classification of the occurrence if it were present.
     *
     * @param occurrenceId Identifier of the occurrence to approve
     */
    void approve(URI occurrenceId);

    /**
     * Removes term occurrence with the specified identifier.
     *
     * @param occurrenceId Identifier of the occurrence to remove
     */
    void remove(URI occurrenceId);

    /**
     * Gets aggregated information about occurrences of the specified term.
     *
     * @param term Term whose occurrences to retrieve
     * @return List of {@code TermOccurrences}
     */
    List<TermOccurrences> getOccurrenceInfo(AbstractTerm term);

    /**
     * Finds all definitional occurrences of the specified term.
     *
     * @param term Term whose occurrences should be returned
     * @return List of term occurrences
     */
    List<TermOccurrence> findAllDefinitionalOf(AbstractTerm term);

    /**
     * Finds all term occurrences whose target points to the specified asset.
     * <p>
     * I.e., these term occurrences appear in the specified asset (file, term definition).
     *
     * @param target Asset to filter by
     * @return List of matching term occurrences
     */
    List<TermOccurrence> findAllTargeting(Asset<?> target);

    /**
     * Removes all occurrences of the specified term.
     *
     * @param term Term whose occurrences to remove
     */
    void removeAllOf(AbstractTerm term);
}
