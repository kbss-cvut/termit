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
package cz.cvut.kbss.termit.event;

import cz.cvut.kbss.termit.model.validation.ValidationResult;
import jakarta.annotation.Nonnull;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Indicates that validation for a set of vocabularies was finished.
 */
public class VocabularyValidationFinishedEvent extends VocabularyEvent {

    /**
     * Vocabulary closure of {@link #vocabularyIri}.
     * IRIs of vocabularies that are imported by {@link #vocabularyIri} and were part of the validation.
     */
    private final List<URI> vocabularyIris;

    private final List<ValidationResult> validationResults;

    /**
     * @param source the source of the event
     * @param originVocabularyIri Vocabulary closure of {@link #vocabularyIri}.
     * @param vocabularyIris IRI of the vocabulary on which the validation was triggered.
     * @param validationResults results of the validation
     */
    public VocabularyValidationFinishedEvent(@Nonnull Object source, @Nonnull URI originVocabularyIri,
                                             @Nonnull Collection<URI> vocabularyIris,
                                             @Nonnull List<ValidationResult> validationResults) {
        super(source, originVocabularyIri);
        // defensive copy
        this.vocabularyIris = new ArrayList<>(vocabularyIris);
        this.validationResults = new ArrayList<>(validationResults);
    }

    @Nonnull
    public List<URI> getVocabularyIris() {
        return Collections.unmodifiableList(vocabularyIris);
    }

    @Nonnull
    public List<ValidationResult> getValidationResults() {
        return Collections.unmodifiableList(validationResults);
    }
}
