package cz.cvut.kbss.termit.event;

import cz.cvut.kbss.termit.model.validation.ValidationResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.springframework.context.ApplicationEvent;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Indicates that validation for a set of vocabularies was finished.
 */
public class VocabularyValidationFinished extends ApplicationEvent {

    /**
     * Vocabulary closure of {@link #originVocabularyIri}.
     * IRIs of vocabularies that are imported by {@link #originVocabularyIri} and were part of the validation.
     */
    @NotNull
    @Unmodifiable
    private final Collection<URI> vocabularyIris;

    @NotNull
    @Unmodifiable
    private final Collection<ValidationResult> validationResults;

    /**
     * IRI of the vocabulary on which the validation was triggered.
     */
    @NotNull
    private final URI originVocabularyIri;

    /**
     * @param source the source of the event
     * @param originVocabularyIri Vocabulary closure of {@link #originVocabularyIri}.
     * @param vocabularyIris IRI of the vocabulary on which the validation was triggered.
     * @param validationResults results of the validation
     */
    public VocabularyValidationFinished(@NotNull Object source, @NotNull URI originVocabularyIri,
                                        @NotNull Collection<URI> vocabularyIris,
                                        @NotNull List<ValidationResult> validationResults) {
        super(source);
        this.vocabularyIris = Collections.unmodifiableCollection(vocabularyIris);
        this.validationResults = Collections.unmodifiableCollection(validationResults);
        this.originVocabularyIri = originVocabularyIri;
    }

    public @NotNull Collection<URI> getVocabularyIris() {
        return vocabularyIris;
    }

    public @NotNull Collection<ValidationResult> getValidationResults() {
        return validationResults;
    }

    public @NotNull URI getOriginVocabularyIri() {
        return originVocabularyIri;
    }
}
