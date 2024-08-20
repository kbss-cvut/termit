package cz.cvut.kbss.termit.util.throttle;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class MockedFutureTask<T> extends FutureTask<T> implements ScheduledFuture<T> {

    private final Callable<T> callable;

    public MockedFutureTask(@NotNull Callable<T> callable) {
        super(callable);
        this.callable = callable;
    }

    public MockedFutureTask(@NotNull Runnable runnable, T result) {
        super(runnable, result);
        this.callable = null;
    }

    public Callable<T> getCallable() {
        return callable;
    }

    @Override
    public long getDelay(@NotNull TimeUnit unit) {
        return 0;
    }

    @Override
    public int compareTo(@NotNull Delayed o) {
        return 0;
    }
}
