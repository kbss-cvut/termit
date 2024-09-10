package cz.cvut.kbss.termit.util.longrunning;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.time.Instant;

public class LongRunningTaskStatus implements Serializable {

    private final State state;

    private final @Nullable Instant startedAt;

    public LongRunningTaskStatus(LongRunningTask task) {
        this.startedAt = task.startedAt().orElse(null);
        this.state = State.of(task);
    }

    public State getState() {
        return state;
    }

    public @Nullable Instant getStartedAt() {
        return startedAt;
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

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("{")
                .append(this.state.name());
        if (startedAt != null) {
            builder.append(", startedAt=").append(startedAt);
        }
        builder.append("}");
        return builder.toString();
    }
}
