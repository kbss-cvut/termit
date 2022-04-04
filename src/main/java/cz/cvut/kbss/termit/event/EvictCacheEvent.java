package cz.cvut.kbss.termit.event;

import org.springframework.context.ApplicationEvent;

/**
 * Indicates that all application caches should be evicted.
 */
public class EvictCacheEvent extends ApplicationEvent {

    public EvictCacheEvent(Object source) {
        super(source);
    }
}
