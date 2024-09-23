package cz.cvut.kbss.termit.event;

import cz.cvut.kbss.termit.util.longrunning.LongRunningTaskStatus;
import jakarta.annotation.Nonnull;
import org.springframework.context.ApplicationEvent;

/**
 * Indicates a status change of a long-running task.
 */
public class LongRunningTaskChangedEvent extends ApplicationEvent {

    private final LongRunningTaskStatus status;

    public LongRunningTaskChangedEvent(@Nonnull Object source, final @Nonnull LongRunningTaskStatus status) {
        super(source);
        this.status = status;
    }

    public @Nonnull LongRunningTaskStatus getStatus() {
        return status;
    }
}
