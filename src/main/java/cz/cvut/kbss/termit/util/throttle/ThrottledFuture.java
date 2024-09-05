package cz.cvut.kbss.termit.util.throttle;

import cz.cvut.kbss.termit.util.Utils;
import cz.cvut.kbss.termit.util.longrunning.LongRunningTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

public class ThrottledFuture<T> implements CachableFuture<T>, LongRunningTask {

    private final ReentrantLock lock = new ReentrantLock();

    private T cachedResult = null;

    private final CompletableFuture<T> future;

    private @Nullable Supplier<T> task;

    /**
     * Access only with acquired {@link #lock}
     */
    private @Nullable Instant completingSince = null;

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
    public static <T> ThrottledFuture<T> canceled() {
        ThrottledFuture<T> f = new ThrottledFuture<>();
        f.cancel(true);
        assert f.isCancelled();
        return f;
    }

    /**
     * @return already done future
     */
    public static <T> ThrottledFuture<T> done(T result) {
        ThrottledFuture<T> f = ThrottledFuture.of(() -> result);
        f.run();
        assert f.isDone();
        return f;
    }

    @Override
    public Optional<T> getCachedResult() {
        return Optional.ofNullable(cachedResult);
    }

    @Override
    public ThrottledFuture<T> setCachedResult(@Nullable final T cachedResult) {
        this.cachedResult = cachedResult;
        return this;
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

    /**
     * Does not execute the task, blocks the current thread until the result is available.
     */
    @Override
    public T get() throws InterruptedException, ExecutionException {
        return future.get();
    }

    /**
     * Does not execute the task, blocks the current thread until the result is available.
     */
    @Override
    public T get(long timeout, @NotNull TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return future.get(timeout, unit);
    }
    /**
     * @param task the new task
     * @return If the current task is already running, was canceled or already completed, returns a new future for the given task.
     * Otherwise, replaces the current task and returns self.
     */
    protected ThrottledFuture<T> update(Supplier<T> task) {
        try {
            boolean locked = lock.tryLock();
            if (!locked || isRunning() || isCompleted()) {
                return ThrottledFuture.of(task);
            }
            this.task = task;
            return this;
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }


    /**
     * Returns future with the task from the specified {@code throttledFuture}.
     * If possible, transfers the task from this object to the specified {@code throttledFuture}.
     *
     * @param target the future to update
     * @return target when current future is already being executed, was canceled or completed.
     * New future when the target is being executed, was canceled or completed.
     */
    protected ThrottledFuture<T> transfer(ThrottledFuture<T> target) {
        try {
            boolean locked = lock.tryLock();
            if (!locked || isRunning() || isCompleted()) {
                return target;
            }

            ThrottledFuture<T> result = target.update(this.task);
            this.task = null;
            return result;
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    protected void run() {
        boolean locked;
        do {
            locked = lock.tryLock();
            if (isRunning() || isCompleted()) {
                return;
            } else if (!locked) {
                Thread.yield();
            }
        } while (!locked);
        completingSince = Utils.timestamp();

        if (task != null) {
            future.complete(task.get());
        } else {
            future.complete(null);
        }
    }

    @Override
    public boolean isRunning() {
        return completingSince != null;
    }

    /**
     * @return true if the future is done or canceled, false otherwise
     */
    @Override
    public boolean isCompleted() {
        return isDone() || isCancelled();
    }

    @Override
    public @Nullable Instant runningSince() {
        return completingSince;
    }
}
