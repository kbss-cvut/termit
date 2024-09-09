package cz.cvut.kbss.termit.util.throttle;

import cz.cvut.kbss.termit.exception.TermItException;
import cz.cvut.kbss.termit.util.Utils;
import cz.cvut.kbss.termit.util.longrunning.LongRunningTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ThrottledFuture<T> implements CacheableFuture<T>, LongRunningTask {

    private final ReentrantLock lock = new ReentrantLock();
    private final ReentrantLock callbackLock = new ReentrantLock();

    private @Nullable T cachedResult = null;

    private final CompletableFuture<T> future;

    private @Nullable Supplier<T> task;

    private final List<Consumer<T>> onCompletion = new ArrayList<>();

    /**
     * Access only with acquired {@link #lock}
     */
    private AtomicReference<@Nullable Instant> startedAt = new AtomicReference<>(null);

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
        return f;
    }

    /**
     * @return already done future
     */
    public static <T> ThrottledFuture<T> done(T result) {
        ThrottledFuture<T> f = ThrottledFuture.of(() -> result);
        f.run();
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
    protected ThrottledFuture<T> update(Supplier<T> task, List<Consumer<T>> onCompletion) {
        boolean locked = false;
        try {
            this.callbackLock.lock();
            locked = lock.tryLock();
            ThrottledFuture<T> updatedFuture = this;
            if (!locked || isRunning() || isDone()) {
                updatedFuture = ThrottledFuture.of(task);
            }
            updatedFuture.task = task;
            updatedFuture.onCompletion.addAll(onCompletion);
            return updatedFuture;
        } finally {
            if (locked) {
                lock.unlock();
            }
            this.callbackLock.unlock();
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
        boolean locked = false;
        try {
            this.callbackLock.lock();
            locked = lock.tryLock();
            if (!locked || isRunning() || isDone()) {
                return target;
            }

            ThrottledFuture<T> result = target.update(this.task, this.onCompletion);
            this.task = null;
            this.onCompletion.clear();
            return result;
        } finally {
            if (locked) {
                lock.unlock();
            }
            this.callbackLock.unlock();
        }
    }

    protected void run() {
        boolean locked = false;
        try {
            do {
                locked = lock.tryLock();
                if (isRunning() || isDone()) {
                    return;
                } else if (!locked) {
                    Thread.yield();
                }
            } while (!locked);
            startedAt.set(Utils.timestamp());

            T result = null;
            if (task != null) {
                result = task.get();
                final T finalResult = result;
                callbackLock.lock();
                onCompletion.forEach(c -> c.accept(finalResult));
                callbackLock.unlock();
            }
            future.complete(result);
        } finally {
            if (locked) {
                lock.unlock();
            }
        }
    }

    @Override
    public boolean isRunning() {
        return startedAt.get() != null && !isDone();
    }

    @Override
    public @NotNull Optional<Instant> startedAt() {
        return Optional.ofNullable(startedAt.get());
    }

    @Override
    public void then(Consumer<T> action) {
        try {
            callbackLock.lock();
            if (future.isDone() && !future.isCancelled()) {
                try {
                    action.accept(future.get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new TermItException(e);
                } catch (ExecutionException e) {
                    throw new TermItException(e);
                }
            } else {
                onCompletion.add(action);
            }
        } finally {
            callbackLock.unlock();
        }
    }
}
