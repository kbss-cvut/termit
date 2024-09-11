package cz.cvut.kbss.termit.util.longrunning;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class LongRunningTaskStatus implements Serializable {

    @NotNull
    private final String name;

    @NotNull
    private final UUID uuid;

    private final State state;

    private final @Nullable Instant startedAt;

    public LongRunningTaskStatus(@NotNull LongRunningTask task) {
        Objects.requireNonNull(task.getName());
        this.name = task.getName();
        this.startedAt = task.startedAt().orElse(null);
        this.state = State.of(task);
        this.uuid = task.getUuid();
    }

    public @NotNull String getName() {
        return name;
    }

    public State getState() {
        return state;
    }

    public @Nullable Instant getStartedAt() {
        return startedAt;
    }

    public @NotNull UUID getUuid() {
        return uuid;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("{").append(this.state.name());
        if (startedAt != null) {
            builder.append(", startedAt=").append(startedAt);
        }
        builder.append(", ");
        builder.append(uuid);
        builder.append("}");
        return builder.toString();
    }

    public enum State {
        PENDING, RUNNING, DONE;

        public static State of(@NotNull LongRunningTask task) {
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
