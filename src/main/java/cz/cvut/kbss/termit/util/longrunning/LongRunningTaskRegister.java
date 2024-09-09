package cz.cvut.kbss.termit.util.longrunning;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * An object that will schedule a long-running tasks
 * @see LongRunningTask
 */
public interface LongRunningTaskRegister {

    /**
     * @return pending and currently running tasks
     */
    @NotNull
    Collection<LongRunningTask> getTasks();
}
