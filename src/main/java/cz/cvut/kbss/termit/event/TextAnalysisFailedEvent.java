package cz.cvut.kbss.termit.event;

import cz.cvut.kbss.termit.exception.TermItException;
import cz.cvut.kbss.termit.model.resource.File;
import jakarta.annotation.Nonnull;

/**
 * Indicates that a text analysis of an asset failed
 */
public class TextAnalysisFailedEvent extends VocabularyEvent {

    private final TermItException exception;

    public TextAnalysisFailedEvent(@Nonnull Object source, TermItException exception, @Nonnull File file) {
        super(source, file.getDocument().getVocabulary());
        this.exception = exception;
    }

    public TermItException getException() {
        return exception;
    }
}
