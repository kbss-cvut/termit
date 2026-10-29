package cz.cvut.kbss.termit.event;

import cz.cvut.kbss.termit.service.repository.removal.TermRemovalParams;
import org.springframework.context.ApplicationEvent;

/**
 * Indicates that the term is about to be removed. The removal is not guaranteed to succeed and all respective changes
 * should be performed in the same transaction.
 */
public class BeforeTermRemovedEvent extends ApplicationEvent {
    /** Term removal params with the Term instance */
    private final TermRemovalParams removalParams;

    public BeforeTermRemovedEvent(Object source, TermRemovalParams removalParams) {
        super(source);
        this.removalParams = removalParams;
    }

    public TermRemovalParams getRemovalParams() {
        return removalParams;
    }
}
