package cz.cvut.kbss.termit.util.longrunning;

import cz.cvut.kbss.termit.event.LongRunningTaskChangedEvent;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationEventPublisher;

/**
 * An object that will schedule a long-running tasks
 * @see LongRunningTask
 */
public abstract class LongRunningTaskScheduler {
    protected final ApplicationEventPublisher eventPublisher;

    protected LongRunningTaskScheduler(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    protected final void notifyTaskChanged(final @NotNull LongRunningTask task) {
        if (task.getName() != null && !task.getName().isBlank()) {
            eventPublisher.publishEvent(new LongRunningTaskChangedEvent(this, task));
        }
    }
}
