package cz.cvut.kbss.termit.util.longrunning;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Optional;

/**
 * An asynchronously running task that is expected to run for some time.
 */
public interface LongRunningTask {

    /**
     * @return true when the task is being actively executed, false otherwise.
     */
    boolean isRunning();

    /**
     * @return true when the task has finished, false otherwise.
     * Returns true regardless of whether the task succeeded.
     */
    boolean isCompleted();

    /**
     * @return a timestamp of the task execution start,
     * or empty if the task execution has not yet started.
     */
    @NotNull
    Optional<Instant> runningSince();
}
