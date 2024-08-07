package cz.cvut.kbss.termit.event;

import cz.cvut.kbss.termit.model.Vocabulary;
import org.springframework.context.ApplicationEvent;

import java.net.URI;

/**
 * Indicates that a Vocabulary will be removed
 * <p>
 * Fired in {@link cz.cvut.kbss.termit.persistence.dao.VocabularyDao#remove(Vocabulary)} right before the vocabulary removal.
 */
public class VocabularyRemovalEvent extends ApplicationEvent {
    private final URI vocabulary;
    public VocabularyRemovalEvent(Object source, URI vocabulary) {
        super(source);
        this.vocabulary = vocabulary;
    }

    public URI getVocabulary() {
        return vocabulary;
    }
}
