package cz.cvut.kbss.termit.util.throttle;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ScheduledFutureTask<T> extends FutureTask<T> implements ScheduledFuture<T> {

    public ScheduledFutureTask(@NotNull Callable<T> callable) {
        super(callable);
    }

    public ScheduledFutureTask(@NotNull Runnable runnable, T result) {
        super(runnable, result);
    }

    @Override
    public long getDelay(@NotNull TimeUnit unit) {
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public int compareTo(@NotNull Delayed o) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
