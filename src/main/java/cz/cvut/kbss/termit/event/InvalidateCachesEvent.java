package cz.cvut.kbss.termit.event;

import org.springframework.context.ApplicationEvent;

/**
 * Indicates that caches in the application should be evicted.
 */
public class InvalidateCachesEvent extends ApplicationEvent {

    public InvalidateCachesEvent(Object source) {
        super(source);
    }
}
