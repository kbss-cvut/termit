package cz.cvut.kbss.termit.event;

import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationEvent;

import java.net.URI;
import java.util.Objects;

/**
 * Base class for vocabulary related events
 */
public abstract class VocabularyEvent extends ApplicationEvent {
    protected final URI vocabularyIri;

    protected VocabularyEvent(Object source, @NotNull URI vocabularyIri) {
        super(source);
        Objects.requireNonNull(vocabularyIri);
        this.vocabularyIri = vocabularyIri;
    }

    /**
     * The identifier of the vocabulary to which this event is bound
     * @return vocabulary IRI
     */
    public URI getVocabularyIri() {
        return vocabularyIri;
    }
}
