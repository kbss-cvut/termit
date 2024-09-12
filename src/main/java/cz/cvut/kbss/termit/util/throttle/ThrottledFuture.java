package cz.cvut.kbss.termit.util.throttle;

import cz.cvut.kbss.termit.exception.TermItException;
import cz.cvut.kbss.termit.util.Utils;
import cz.cvut.kbss.termit.util.longrunning.LongRunningTask;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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

    private final UUID uuid = UUID.randomUUID();

    private @Nullable T cachedResult = null;

    private final CompletableFuture<T> future;

    private @Nullable Supplier<T> task;

    private final List<Consumer<T>> onCompletion = new ArrayList<>();

    private final AtomicReference<Instant> startedAt = new AtomicReference<>(null);

    private @Nullable String name = null;

    private ThrottledFuture(@NonNull final Supplier<T> task) {
        this.task = task;
        future = new CompletableFuture<>();
    }

    protected ThrottledFuture() {
        future = new CompletableFuture<>();
    }

    public static <T> ThrottledFuture<T> of(@NonNull final Supplier<T> supplier) {
        return new ThrottledFuture<>(supplier);
    }

    public static ThrottledFuture<Void> of(@NonNull final Runnable runnable) {
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
        f.run(null);
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
    public T get(long timeout, @NonNull TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return future.get(timeout, unit);
    }
    /**
     * @param task the new task
     * @return If the current task is already running, was canceled or already completed, returns a new future for the given task.
     * Otherwise, replaces the current task and returns self.
     */
    protected ThrottledFuture<T> update(Supplier<T> task, @NonNull List<Consumer<T>> onCompletion) {
        boolean locked = false;
        try {
            locked = lock.tryLock();
            this.callbackLock.lock();
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
     * If the task was successfully transferred, this future is canceled.
     *
     * @param target the future to update
     * @return target when current future is already being executed, was canceled or completed.
     * New future when the target is being executed, was canceled or completed.
     */
    protected ThrottledFuture<T> transfer(ThrottledFuture<T> target) {
        boolean locked = false;
        try {
            locked = lock.tryLock();
            this.callbackLock.lock();
            if (!locked || isRunning() || isDone()) {
                return target;
            }

            ThrottledFuture<T> result = target.update(this.task, this.onCompletion);
            this.task = null;
            this.onCompletion.clear();
            this.cancel(false);
            return result;
        } finally {
            if (locked) {
                lock.unlock();
            }
            this.callbackLock.unlock();
        }
    }

    /**
     * Executes the task associated with this future
     * @param startedCallback called once {@link #startedAt} is set and so execution is considered as running.
     */
    protected void run(@Nullable Consumer<ThrottledFuture<T>> startedCallback) {
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
            if (startedCallback != null) {
                startedCallback.accept(this);
            }

            try {
                T result = null;
                if (task != null) {
                    result = task.get();
                    final T finalResult = result;
                    callbackLock.lock();
                    onCompletion.forEach(c -> c.accept(finalResult));
                    callbackLock.unlock();
                }
                future.complete(result);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        } finally {
            if (locked) {
                lock.unlock();
            }
        }
    }

    @Override
    public @Nullable String getName() {
        return this.name;
    }

    protected void setName(@Nullable String name) {
        this.name = name;
    }

    @Override
    public boolean isRunning() {
        return startedAt.get() != null && !isDone();
    }

    @Override
    public @NonNull Optional<Instant> startedAt() {
        return Optional.ofNullable(startedAt.get());
    }

    @Override
    public @NonNull UUID getUuid() {
        return uuid;
    }

    @Override
    public ThrottledFuture<T> then(Consumer<T> action) {
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
        return this;
    }
}
