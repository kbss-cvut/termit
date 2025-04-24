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
package cz.cvut.kbss.termit.persistence.context;

import cz.cvut.kbss.termit.model.Vocabulary;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/**
 * Maps vocabularies to repository contexts in which they are stored.
 */
public interface VocabularyContextMapper {

    /**
     * Gets identifier of the repository context in which the specified vocabulary is stored.
     *
     * @param vocabulary Vocabulary whose context to retrieve. A reference is sufficient
     * @return Repository context identifier
     */
    default URI getVocabularyContext(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        return getVocabularyContext(vocabulary.getUri());
    }

    /**
     * Gets identifier of the repository context in which vocabulary with the specified identifier is stored.
     * <p>
     * If the vocabulary does not exist yet (and thus has no repository context), the vocabulary identifier is returned
     * as context.
     *
     * @param vocabularyUri Vocabulary identifier
     * @return Repository context identifier
     * @throws cz.cvut.kbss.termit.exception.AmbiguousVocabularyContextException In case multiple contexts for a
     *                                                                           vocabulary are found
     */
    URI getVocabularyContext(URI vocabularyUri);

    /**
     * Resolves identifier of the vocabulary stored in the specified repository context.
     *
     * @param contextUri Context identifier
     * @return Optional vocabulary identifier
     */
    Optional<URI> getVocabularyInContext(URI contextUri);
}
