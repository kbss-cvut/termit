package cz.cvut.kbss.termit.event;

import org.springframework.context.ApplicationEvent;

import java.net.URI;

/**
 * Indicates that a Vocabulary will be removed
 */
public class VocabularyWillBeRemovedEvent extends ApplicationEvent {
    private final URI vocabulary;

    public VocabularyWillBeRemovedEvent(Object source, URI vocabulary) {
        super(source);
        this.vocabulary = vocabulary;
    }

    public URI getVocabulary() {
        return vocabulary;
    }
}
