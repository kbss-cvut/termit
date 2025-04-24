/*
 * TermIt
 * Copyright (C) 2025 Czech Technical University in Prague
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.util.throttle;

import cz.cvut.kbss.termit.exception.TermItException;
import cz.cvut.kbss.termit.util.Utils;
import cz.cvut.kbss.termit.util.longrunning.LongRunningTask;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

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

public class ThrottledFuture<T> implements CacheableFuture<T>, ChainableFuture<T, ThrottledFuture<T>>, LongRunningTask {
    private static final int TRY_LOCK_MILLIS_TIMEOUT = 500;
    private final ReentrantLock lock = new ReentrantLock();
    private final ReentrantLock callbackLock = new ReentrantLock();

    private final UUID uuid = UUID.randomUUID();

    private @Nullable T cachedResult = null;

    private final CompletableFuture<T> future;

    private @Nullable Supplier<T> task;

    private final List<Consumer<ThrottledFuture<T>>> onCompletion = new ArrayList<>();

    private final AtomicReference<Instant> startedAt = new AtomicReference<>(null);

    private @Nullable String name = null;

    private ThrottledFuture(@Nonnull final Supplier<T> task) {
        this.task = task;
        future = new CompletableFuture<>();
    }

    protected ThrottledFuture() {
        future = new CompletableFuture<>();
    }

    public static <T> ThrottledFuture<T> of(@Nonnull final Supplier<T> supplier) {
        return new ThrottledFuture<>(supplier);
    }

    public static ThrottledFuture<Void> of(@Nonnull final Runnable runnable) {
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
        final boolean wasCanceled = isCancelled();
        if(!future.cancel(mayInterruptIfRunning)) {
            return false;
        }

        if (!wasCanceled && task != null) {
            callbackLock.lock();
            try {
                onCompletion.forEach(c -> c.accept(this));
                onCompletion.clear(); // remove executed callbacks
            } finally {
                callbackLock.unlock();
            }
        }
        return true;
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
    public T get(long timeout, @Nonnull TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        return future.get(timeout, unit);
    }
    /**
     * @param task the new task
     * @return If the current task is already running, was canceled or already completed, returns a new future for the given task.
     * Otherwise, replaces the current task and returns self.
     */
    protected ThrottledFuture<T> update(Supplier<T> task, @Nonnull List<Consumer<ThrottledFuture<T>>> onCompletion) {
        boolean locked = false;
        try {
            locked = lock.tryLock(TRY_LOCK_MILLIS_TIMEOUT, TimeUnit.MILLISECONDS);
            this.callbackLock.lock();
            ThrottledFuture<T> updatedFuture = this;
            if (!locked || isRunning() || isDone()) {
                updatedFuture = ThrottledFuture.of(task);
            }
            updatedFuture.task = task;
            updatedFuture.onCompletion.addAll(onCompletion);
            return updatedFuture;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TermItException(e);
        } finally {
            if (callbackLock.isHeldByCurrentThread()) {
                this.callbackLock.unlock();
            }
            if (locked) {
                lock.unlock();
            }
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
            locked = lock.tryLock(TRY_LOCK_MILLIS_TIMEOUT, TimeUnit.MILLISECONDS);
            this.callbackLock.lock();
            if (!locked || isRunning() || isDone()) {
                return target;
            }

            ThrottledFuture<T> result = target.update(this.task, this.onCompletion);
            this.task = null;
            this.onCompletion.clear();
            this.cancel(false);
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TermItException(e);
        } finally {
            if (callbackLock.isHeldByCurrentThread()) {
                this.callbackLock.unlock();
            }
            if (locked) {
                lock.unlock();
            }
        }
    }

    /**
     * Executes the task associated with this future.
     *
     * @param startedCallback called once {@link #startedAt} is set and so execution is considered as running.
     */
    protected void run(@Nullable Consumer<ThrottledFuture<T>> startedCallback) {
        boolean locked = false;
        try {
            do {
                locked = lock.tryLock(TRY_LOCK_MILLIS_TIMEOUT, TimeUnit.MILLISECONDS);
                if (isRunning() || isDone()) {
                    return;
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
                }
                future.complete(result);
            } catch (Exception e) {
                future.completeExceptionally(e);
            } finally {
                if (task != null) {
                    callbackLock.lock();
                    try {
                        onCompletion.forEach(c -> c.accept(this));
                        onCompletion.clear(); // remove executed callbacks
                    } finally {
                        callbackLock.unlock();
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TermItException(e);
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
    public @Nonnull Optional<Instant> startedAt() {
        return Optional.ofNullable(startedAt.get());
    }

    @Override
    public @Nonnull UUID getUuid() {
        return uuid;
    }

    @Override
    public ThrottledFuture<T> then(Consumer<ThrottledFuture<T>> action) {
        try {
            callbackLock.lock();
            if (future.isDone()) {
                action.accept(this);
            } else {
                onCompletion.add(action);
            }
        } finally {
            if (callbackLock.isHeldByCurrentThread()) {
                callbackLock.unlock();
            }
        }
        return this;
    }

    /**
     * When the {@code other} future is not completed,
     * all {@link #onCompletion} callbacks are merged into this future using {@link #then(Consumer)},
     * removed from the {@code other} and the {@code other} future is canceled.
     * <p>
     * When this future is already done, then callbacks from {@code other} are executed synchronously using this future.
     * <p>
     * Does nothing when the other future is completed.
     *
     * @param other the future from where completion callbacks should be removed
     */
    public void consumeCallbacks(ThrottledFuture<T> other) {
        try {
            boolean locked = false;
            do {
                locked = lock.tryLock(TRY_LOCK_MILLIS_TIMEOUT, TimeUnit.MILLISECONDS);
                if (other.isRunning()) {
                    return;
                }
            } while (!locked);

            other.callbackLock.lock();
            if (!other.isDone() && !other.isRunning()) {
                final List<Consumer<ThrottledFuture<T>>> otherCallbacks = new ArrayList<>(other.onCompletion);
                other.onCompletion.clear();
                this.then(ignored -> otherCallbacks.forEach(otherCallback -> otherCallback.accept(this)));
                other.cancel(false);
            }
            // the other future is already done so we cannot consume callbacks
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TermItException(e);
        } finally {
            if (other.callbackLock.isHeldByCurrentThread()) {
                other.callbackLock.lock();
            }
            if (other.lock.isHeldByCurrentThread()) {
                other.lock.unlock();
            }
        }
    }

    /**
     * @return {@code true} if this future completed
     * exceptionally or was cancelled.
     */
    public boolean isCompletedExceptionally() {
        return future.isCompletedExceptionally();
    }
}
