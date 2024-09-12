package cz.cvut.kbss.termit.util.longrunning;

import org.springframework.lang.NonNull;

/**
 * An object that will schedule a long-running tasks
 * @see LongRunningTask
 */
public abstract class LongRunningTaskScheduler {
    private final LongRunningTasksRegistry registry;

    protected LongRunningTaskScheduler(LongRunningTasksRegistry registry) {
        this.registry = registry;
    }

    protected final void notifyTaskChanged(final @NonNull LongRunningTask task) {
        final String name = task.getName();
        if (name != null && !name.isBlank()) {
            registry.onTaskChanged(task);
        }
    }
}
