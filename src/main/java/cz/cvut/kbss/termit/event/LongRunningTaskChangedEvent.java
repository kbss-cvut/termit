package cz.cvut.kbss.termit.event;

import cz.cvut.kbss.termit.util.longrunning.LongRunningTask;
import cz.cvut.kbss.termit.util.longrunning.LongRunningTaskStatus;
import org.springframework.context.ApplicationEvent;
import org.springframework.lang.NonNull;

/**
 * Indicates a status change of a long-running task.
 */
public class LongRunningTaskChangedEvent extends ApplicationEvent {

    private final LongRunningTaskStatus status;

    public LongRunningTaskChangedEvent(@NonNull Object source, final @NonNull LongRunningTaskStatus status) {
        super(source);
        this.status = status;
    }

    public @NonNull LongRunningTaskStatus getStatus() {
        return status;
    }
}
