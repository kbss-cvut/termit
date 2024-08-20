package cz.cvut.kbss.termit.environment.util;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Utility class implementing future interface
 */
public class MockedFuture<T> implements Future<T> {

    private final T result;

    private boolean cancelled;

    private boolean done;

    public MockedFuture(T result) {
        this.result = result;
        this.done = true;
        this.cancelled = false;
    }


    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (done) {
            return false;
        }
        this.cancelled = true;
        return true;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        return result;
    }

    @Override
    public T get(long timeout, @NotNull TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return result;
    }
}
