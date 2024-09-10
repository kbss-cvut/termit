package cz.cvut.kbss.termit.event;

import cz.cvut.kbss.termit.util.longrunning.LongRunningTask;
import cz.cvut.kbss.termit.util.longrunning.LongRunningTaskStatus;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationEvent;

import java.util.Objects;

/**
 * Indicates a status change of a long-running task.
 */
public class LongRunningTaskChangedEvent extends ApplicationEvent {

    @NotNull
    private final String name;

    @NotNull
    private final LongRunningTaskStatus status;

    public LongRunningTaskChangedEvent(@NotNull Object source, final @NotNull LongRunningTask longRunningTask) {
        super(source);
        this.name = Objects.requireNonNull(longRunningTask.getName());
        this.status = new LongRunningTaskStatus(longRunningTask);
    }

    public @NotNull String getName() {
        return name;
    }

    public @NotNull LongRunningTaskStatus getStatus() {
        return status;
    }

    @Override
    public Object getSource() {
        return super.getSource();
    }
}
