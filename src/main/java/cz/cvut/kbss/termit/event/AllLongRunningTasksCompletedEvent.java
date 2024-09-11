package cz.cvut.kbss.termit.event;

import org.springframework.context.ApplicationEvent;

/**
 * Indicates that all long-running tasks were completed and there is not running or pending task.
 */
public class AllLongRunningTasksCompletedEvent extends ApplicationEvent {

    public AllLongRunningTasksCompletedEvent(Object source) {
        super(source);
    }
}
