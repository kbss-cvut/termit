package cz.cvut.kbss.termit.event;

import org.springframework.context.ApplicationEvent;

/**
 * Indicates that the long-running task queue should be cleared.
 */
public class ClearLongRunningTaskQueueEvent extends ApplicationEvent {
    public ClearLongRunningTaskQueueEvent(Object source) {
        super(source);
    }
}
