package cz.cvut.kbss.termit.service.repository.migration;

import cz.cvut.kbss.termit.util.Utils;
import cz.cvut.kbss.termit.util.longrunning.LongRunningTask;

import jakarta.annotation.Nonnull;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Task indicating that there is an ongoing Identifier migration */
public class IriMigrationLongRunningTask implements LongRunningTask {
    private final UUID uuid = UUID.randomUUID();
    private final AtomicReference<Instant> startedAt = new AtomicReference<>(null);
    private final AtomicBoolean isDone = new AtomicBoolean(false);

    @Override
    public String getName() {
        // FE translation key longrunningtasks.name.migration.identifier
        return "migration.identifier";
    }

    /** @return true when the task is being actively executed, false otherwise. */
    @Override
    public boolean isRunning() {
        return startedAt.get() != null && !isDone();
    }

    /**
     * Returns {@code true} if this task completed.
     *
     * <p>Completion may be due to normal termination, an exception, or cancellation -- in all of these cases, this
     * method will return {@code true}.
     *
     * @return {@code true} if this task completed
     */
    @Override
    public boolean isDone() {
        return isDone.get();
    }

    /** @return a timestamp of the task execution start, or empty if the task execution has not yet started. */
    @Nonnull
    @Override
    public Optional<Instant> startedAt() {
        return Optional.ofNullable(startedAt.get());
    }

    @Nonnull
    @Override
    public UUID getUuid() {
        return uuid;
    }

    void markStarted() {
        startedAt.set(Utils.timestamp());
    }

    void markAsDone() {
        isDone.set(true);
    }
}
