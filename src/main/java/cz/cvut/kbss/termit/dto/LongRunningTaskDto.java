package cz.cvut.kbss.termit.dto;

import cz.cvut.kbss.termit.event.LongRunningTaskChangedEvent;
import cz.cvut.kbss.termit.util.longrunning.LongRunningTaskStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.time.Instant;

public class LongRunningTaskDto implements Serializable {

    @NotNull
    private final String name;

    @NotNull
    private final LongRunningTaskStatus.State state;

    @Nullable
    private final Instant startedAt;

    public LongRunningTaskDto(LongRunningTaskChangedEvent event) {
        this.name = event.getName();
        this.state = event.getStatus().getState();
        this.startedAt = event.getStatus().getStartedAt();
    }

    public @NotNull String getName() {
        return name;
    }

    public @NotNull LongRunningTaskStatus.State getState() {
        return state;
    }

    public @Nullable Instant getStartedAt() {
        return startedAt;
    }
}
