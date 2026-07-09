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
package cz.cvut.kbss.termit.workspace;

import cz.cvut.kbss.termit.model.Vocabulary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/**
 * Provides access to editable vocabularies.
 * <p>
 * Based on configuration, all vocabularies in a repository may be editable, or just a subset of them opened for
 * editing. When only a subset is open for editing, it assumed that it represents working copies of canonical versions
 * of the vocabularies.
 * <p>
 * This bean then allows checking whether a vocabulary is editable and what repository context it occupies (important
 * especially for the working copy scenario).
 */
@Component
public class EditableVocabularies implements Serializable {

    private static final Logger LOG = LoggerFactory.getLogger(EditableVocabularies.class);

    private final boolean allVocabulariesEditable = true;

    /**
     * Clears the registered contexts.
     */
    public void clear() {

    }

    public boolean isEditable(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        return isEditable(vocabulary.getUri());
    }

    public boolean isEditable(URI vocabularyUri) {
        Objects.requireNonNull(vocabularyUri);
        return allVocabulariesEditable;
    }

    public Optional<URI> getVocabularyContext(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        return getVocabularyContext(vocabulary.getUri());
    }

    public Optional<URI> getVocabularyContext(URI vocabularyUri) {
        Objects.requireNonNull(vocabularyUri);
        return Optional.empty();
    }
}
