package cz.cvut.kbss.termit.event;

import org.springframework.context.ApplicationEvent;

/**
 * Indicates that a vocabulary has been created.
 */
public class VocabularyCreatedEvent extends ApplicationEvent {

    public VocabularyCreatedEvent(Object source) {
        super(source);
    }
}
