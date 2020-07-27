package cz.cvut.kbss.termit.event;

import org.springframework.context.ApplicationEvent;

/**
 * Represents an event published when last modified timestamps should be reset to the current time.
 */
public class RefreshLastModifiedEvent extends ApplicationEvent {

    public RefreshLastModifiedEvent(Object source) {
        super(source);
    }
}
