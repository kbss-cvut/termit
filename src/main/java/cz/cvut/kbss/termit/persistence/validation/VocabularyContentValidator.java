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
package cz.cvut.kbss.termit.persistence.validation;

import cz.cvut.kbss.termit.model.validation.ValidationResult;
import cz.cvut.kbss.termit.util.throttle.ThrottledFuture;
import jakarta.annotation.Nonnull;

import java.net.URI;
import java.util.Collection;

/**
 * Allows validating the content of vocabularies based on preconfigured rules.
 */
public interface VocabularyContentValidator {

    /**
     * Validates the content of vocabularies with the specified identifiers.
     * <p>
     * The vocabularies are validated together, as a single unit.
     *
     * @param originVocabularyIri the origin vocabulary IRI
     * @param vocabularyIris Vocabulary identifiers (including {@code originVocabularyIri}
     * @return List of violations of validation rules. Empty list if there are no violations
     */
    @Nonnull
    ThrottledFuture<Collection<ValidationResult>> validate(@Nonnull URI originVocabularyIri, @Nonnull Collection<URI> vocabularyIris);
}
