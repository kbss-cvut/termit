package cz.cvut.kbss.termit.event;

import org.springframework.context.ApplicationEvent;
import org.springframework.lang.NonNull;

import java.net.URI;
import java.util.Objects;

/**
 * Base class for vocabulary related events
 */
public abstract class VocabularyEvent extends ApplicationEvent {
    protected final URI vocabularyIri;

    protected VocabularyEvent(@NonNull Object source, @NonNull URI vocabularyIri) {
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
