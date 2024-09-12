package cz.cvut.kbss.termit.util.longrunning;

import cz.cvut.kbss.termit.event.LongRunningTaskChangedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.lang.NonNull;

/**
 * An object that will schedule a long-running tasks
 * @see LongRunningTask
 */
public abstract class LongRunningTaskScheduler {
    protected final ApplicationEventPublisher eventPublisher;

    protected LongRunningTaskScheduler(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    protected final void notifyTaskChanged(final @NonNull LongRunningTask task) {
        if (task.getName() != null && !task.getName().isBlank()) {
            eventPublisher.publishEvent(new LongRunningTaskChangedEvent(this, task));
        }
    }
}
