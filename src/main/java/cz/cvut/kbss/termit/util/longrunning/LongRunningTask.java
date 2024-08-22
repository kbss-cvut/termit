package cz.cvut.kbss.termit.util.longrunning;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;

public interface LongRunningTask {

    boolean isRunning();
    boolean isCompleted();

    @Nullable
    Instant runningSince();
}
