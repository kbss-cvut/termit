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
package cz.cvut.kbss.termit.persistence.relationship;

import jakarta.annotation.Nonnull;

import java.net.URI;
import java.util.Set;

/**
 * Resolves vocabulary relationships.
 */
public interface VocabularyRelationshipResolver {

    /**
     * Resolves the set of related vocabularies of the specified vocabulary.
     * <p>
     * It is expected that the resolution is recursive, i.e. not only directly related vocabularies are included.
     *
     * @param vocabulary Identifier of vocabulary whose related vocabularies to resolve
     * @return Set of related vocabulary identifiers
     */
    @Nonnull
    Set<URI> getRelatedVocabularies(@Nonnull URI vocabulary);
}
