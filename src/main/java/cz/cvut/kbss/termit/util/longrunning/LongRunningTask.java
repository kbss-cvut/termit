package cz.cvut.kbss.termit.util.longrunning;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * An asynchronously running task that is expected to run for some time.
 */
public interface LongRunningTask {

    @Nullable
    String getName();

    /**
     * @return true when the task is being actively executed, false otherwise.
     */
    boolean isRunning();

    /**
     * Returns {@code true} if this task completed.
     * <p>
     * Completion may be due to normal termination, an exception, or
     * cancellation -- in all of these cases, this method will return
     * {@code true}.
     *
     * @return {@code true} if this task completed
     */
    boolean isDone();

    /**
     * @return a timestamp of the task execution start,
     * or empty if the task execution has not yet started.
     */
    @NonNull
    Optional<Instant> startedAt();

    @NonNull
    UUID getUuid();
}
