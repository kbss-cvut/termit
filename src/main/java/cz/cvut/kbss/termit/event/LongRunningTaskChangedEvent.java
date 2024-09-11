package cz.cvut.kbss.termit.event;

import cz.cvut.kbss.termit.util.longrunning.LongRunningTask;
import cz.cvut.kbss.termit.util.longrunning.LongRunningTaskStatus;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationEvent;

/**
 * Indicates a status change of a long-running task.
 */
public class LongRunningTaskChangedEvent extends ApplicationEvent {

    @NotNull
    private final LongRunningTaskStatus status;

    public LongRunningTaskChangedEvent(@NotNull Object source, final @NotNull LongRunningTask longRunningTask) {
        super(source);
        this.status = new LongRunningTaskStatus(longRunningTask);
    }

    public @NotNull LongRunningTaskStatus getStatus() {
        return status;
    }
}
