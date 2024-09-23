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
