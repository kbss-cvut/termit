package cz.cvut.kbss.termit.util.longrunning;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class LongRunningTaskStatus implements Serializable {

    private final String name;

    private final UUID uuid;

    private final State state;

    private final Instant startedAt;

    public LongRunningTaskStatus(@NonNull LongRunningTask task) {
        Objects.requireNonNull(task.getName());
        this.name = task.getName();
        this.startedAt = task.startedAt().orElse(null);
        this.state = State.of(task);
        this.uuid = task.getUuid();
    }

    public @NonNull String getName() {
        return name;
    }

    public State getState() {
        return state;
    }

    public @Nullable Instant getStartedAt() {
        return startedAt;
    }

    public @NonNull UUID getUuid() {
        return uuid;
    }

    @Override
    public String toString() {
        return "{" + state.name() + (startedAt == null ? "" : ", startedAt=" + startedAt) + ", " + uuid + "}";
    }

    public enum State {
        PENDING, RUNNING, DONE;

        public static State of(@NonNull LongRunningTask task) {
            if (task.isRunning()) {
                return RUNNING;
            } else if (task.isDone()) {
                return DONE;
            } else {
                return PENDING;
            }
        }
    }
}
