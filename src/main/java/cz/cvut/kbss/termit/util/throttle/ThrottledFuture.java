package cz.cvut.kbss.termit.util.throttle;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

public class ThrottledFuture<T> implements Future<T> {

    private final Object lock = new Object();

    private final CompletableFuture<T> future;

    private @Nullable Supplier<T> task;

    /**
     * Access only with acquired {@link #lock}
     */
    private boolean completing = false;

    private ThrottledFuture(@NotNull final Supplier<T> task) {
        this.task = task;
        future = new CompletableFuture<>();
    }

    protected ThrottledFuture() {
        future = new CompletableFuture<>();
    }

    public static <T> ThrottledFuture<T> of(@NotNull final Supplier<T> supplier) {
        return new ThrottledFuture<>(supplier);
    }

    public static ThrottledFuture<Void> of(@NotNull final Runnable runnable) {
        return new ThrottledFuture<>(() -> {
            runnable.run();
            return null;
        });
    }

    /**
     * @return already canceled future
     */
    public static ThrottledFuture<Void> canceled() {
        ThrottledFuture<Void> f = new ThrottledFuture<>();
        f.cancel(true);
        return f;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return future.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
        return future.isCancelled();
    }

    @Override
    public boolean isDone() {
        return future.isDone();
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        return future.get();
    }

    @Override
    public T get(long timeout, @NotNull TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return future.get(timeout, unit);
    }

    /**
     * @param task the new task
     * @return If the current task is already running, was canceled or already completed, returns a new future for the given task.
     * Otherwise, replaces the current task and returns this.
     */
    protected ThrottledFuture<T> update(Supplier<T> task) {
        synchronized (lock) {
            if (completing || future.isCancelled() || future.isDone()) {
                return ThrottledFuture.of(task);
            }
            this.task = task;
            return this;
        }
    }


    /**
     * Transfers the task from this object to the specified {@code throttledFuture}.
     * If the current task is already running, canceled or completed, this method has no effect.
     *
     * @param target the future to update
     * @return target when current future is already being executed, was canceled or completed.
     * New future when the target is being executed, was canceled or completed.
     */
    protected ThrottledFuture<T> transfer(ThrottledFuture<T> target) {
        synchronized (lock) {
            if (completing || future.isCancelled() || future.isDone()) {
                return target;
            }

            ThrottledFuture<T> result = target.update(this.task);
            this.task = null;
            return result;
        }
    }

    protected void run() {
        synchronized (lock) {
            if (completing || future.isCancelled() || future.isDone()) {
                return;
            }
            completing = true;
        }

        if (task != null) {
            future.complete(task.get());
        } else {
            future.complete(null);
        }
    }
}
