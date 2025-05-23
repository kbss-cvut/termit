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

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ThrottledFutureTest {

    @Test
    void cancelledFactoryMethodReturnsCancelledFuture() {
        final ThrottledFuture<?> future = ThrottledFuture.canceled();
        assertTrue(future.isCancelled());
        assertTrue(future.isDone()); // future is done when it is cancelled
        assertFalse(future.isRunning());
    }

    @Test
    void doneFactoryMethodReturnsDoneFuture() throws Throwable {
        final Object result = new Object();
        final ThrottledFuture<Object> future = ThrottledFuture.done(result);
        assertFalse(future.isCancelled());
        assertTrue(future.isDone());
        assertFalse(future.isRunning());
        final Object futureResult = future.get(1, TimeUnit.SECONDS);
        assertNotNull(futureResult);
        assertEquals(result, futureResult);
    }

    @Test
    void getNowReturnsCacheWhenCacheIsAvailable() {
        final Object cache = new Object();
        final ThrottledFuture<Object> future = ThrottledFuture.of(Object::new).setCachedResult(cache);
        final Optional<Object> cached = future.getNow();
        assertNotNull(cached);
        assertTrue(cached.isPresent());
        assertEquals(cache, cached.get());
    }

    @Test
    void getNowReturnsEmptyWhenCacheIsNotAvailable() {
        final ThrottledFuture<Object> future = ThrottledFuture.of(Object::new);
        final Optional<Object> cached = future.getNow();
        assertNotNull(cached);
        assertTrue(cached.isEmpty());
    }

    @Test
    void getNowReturnsEmptyWhenCacheIsNull() {
        final ThrottledFuture<Object> future = ThrottledFuture.of(Object::new).setCachedResult(null);
        final Optional<Object> cached = future.getNow();
        assertNotNull(cached);
        assertTrue(cached.isEmpty());
    }

    @Test
    void thenActionIsExecutedSynchronouslyWhenFutureIsAlreadyDoneAndNotCanceled() {
        final ThrottledFuture<?> future = ThrottledFuture.of(() -> null);
        final AtomicBoolean completed = new AtomicBoolean(false);
        final AtomicReference<Object> futureResult = new AtomicReference<>(null);
        future.run(null);
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        future.then(fResult -> {
            completed.set(true);
            futureResult.set(fResult);
        });
        assertTrue(completed.get());
        assertEquals(future, futureResult.get());
    }

    @Test
    void thenActionIsExecutedWhenFutureIsAlreadyCancelled() {
        final ThrottledFuture<?> future = ThrottledFuture.of(Object::new);
        final AtomicBoolean completed = new AtomicBoolean(false);
        future.cancel(false);
        assertTrue(future.isCancelled());
        future.then(result -> completed.set(true));
        assertTrue(completed.get());
    }

    @Test
    void thenActionIsExecutedOnceFutureIsRun() {
        final AtomicBoolean completed = new AtomicBoolean(false);
        final AtomicReference<Object> fResult = new AtomicReference<>(null);
        final ThrottledFuture<?> future = ThrottledFuture.of(() -> null);
        future.then(futureResult -> {
            completed.set(true);
            fResult.set(futureResult);
        });
        assertNull(fResult.get());
        assertFalse(completed.get()); // action was not executed yet
        future.run(null);
        assertTrue(completed.get());
        assertEquals(future, fResult.get());
    }

    @Test
    void thenActionIsExecutedOnceFutureIsCancelled() {
        final Object result = new Object();
        final AtomicBoolean completed = new AtomicBoolean(false);
        final ThrottledFuture<?> future = ThrottledFuture.of(() -> result);
        future.then(futureResult -> completed.set(true));
        assertFalse(completed.get()); // action was not executed yet
        future.cancel(false);
        assertTrue(completed.get());
    }

    @Test
    void thenActionIsExecutedOnlyOnceWhenFutureIsCancelled() {
        final AtomicInteger executionCount = new AtomicInteger(0);
        final ThrottledFuture<?> future = ThrottledFuture.of(() -> null);
        future.then(f -> executionCount.incrementAndGet());
        assertEquals(0, executionCount.get());
        future.cancel(false);
        assertEquals(1, executionCount.get());
        future.cancel(false);
        future.cancel(true);
        assertEquals(1, executionCount.get());
    }

    @Test
    void thenActionIsExecutedWhenFutureCompletesExceptionally() {
        final AtomicBoolean completed = new AtomicBoolean(false);
        final ThrottledFuture<?> future = ThrottledFuture.of(() -> {
            throw new RuntimeException();
        });
        future.then(futureResult -> completed.set(true));
        assertFalse(completed.get());
        future.run(null);
        assertTrue(completed.get());
    }

    @Test
    void isCompletedExceptionallyReturnsTrueWhenFutureCompletesExceptionally() {
        final ThrottledFuture<?> future = ThrottledFuture.of(() -> {
            throw new RuntimeException();
        });
        future.run(null);
        assertTrue(future.isCompletedExceptionally());
    }

    @Test
    void isCompletedExceptionallyReturnsFalseWhenFutureCompletesNormally() {
        final ThrottledFuture<?> future = ThrottledFuture.of(() -> null);
        future.run(null);
        assertFalse(future.isCompletedExceptionally());
        assertFalse(future.isCancelled());
        assertTrue(future.isDone());
    }

    @Test
    void isCompletedExceptionallyReturnsTrueWhenFutureIsCancelled() {
        final ThrottledFuture<?> future = ThrottledFuture.of(() -> null);
        future.cancel(false);
        assertTrue(future.isCompletedExceptionally());
        assertTrue(future.isCancelled());
        assertTrue(future.isDone());
    }

    @Test
    void thenActionIsExecutedWhenFutureIsAlreadyCompletedExceptionally() {
        final AtomicBoolean completed = new AtomicBoolean(false);
        final ThrottledFuture<?> future = ThrottledFuture.of(() -> {
            throw new RuntimeException();
        });
        future.run(null);
        assertFalse(completed.get());
        future.then(futureResult -> completed.set(true));
        assertTrue(completed.get());
    }

    @Test
    void callingRunWillExecuteFutureOnlyOnce() {
        AtomicInteger count = new AtomicInteger(0);
        final ThrottledFuture<?> future = ThrottledFuture.of(() -> {
            count.incrementAndGet();
        });

        future.run(null);
        final Optional<Instant> runningSince = future.startedAt();
        assertTrue(runningSince.isPresent());
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        assertFalse(future.isRunning());

        future.run(null);
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        assertFalse(future.isRunning());

        // verify that timestamp did not change
        assertTrue(future.startedAt().isPresent());
        assertEquals(runningSince.get(), future.startedAt().get());
    }

    /**
     * Verifies locks and that second thread exists fast when calls run on already running future.
     */
    @Test
    void callingRunWillExecuteFutureOnlyOnceAndWontBlockSecondThreadAsync() throws Throwable {
        AtomicBoolean allowExit = new AtomicBoolean(false);
        AtomicInteger count = new AtomicInteger(0);
        final ThrottledFuture<?> future = ThrottledFuture.of(() -> {
            count.incrementAndGet();
            while (!allowExit.get()) {
                Thread.yield();
            }
        });
        final Thread threadA = new Thread(() -> future.run(null));
        final Thread threadB = new Thread(() -> future.run(null));
        threadA.start();

        await("count incrementation").atMost(Duration.ofSeconds(30)).until(() -> count.get() > 0);
        // now there is a threadA spinning in the future task
        // locks in the future should be held
        assertTrue(future.isRunning());
        assertFalse(future.isDone());
        assertFalse(future.isCancelled());

        final Optional<Instant> runningSince = future.startedAt();
        assertTrue(runningSince.isPresent());

        threadB.start();

        // thread B should not be blocked
        await("threadB start").atMost(Duration.ofSeconds(30)).until(() -> threadB.getState().equals(Thread.State.TERMINATED));
        assertTrue(future.isRunning());

        allowExit.set(true);
        threadA.join(60 * 1000);
        threadB.join(60 * 1000);

        assertFalse(threadA.isAlive());
        assertFalse(threadB.isAlive());

        assertEquals(1, count.get());
        assertTrue(future.startedAt().isPresent());
        assertEquals(runningSince.get(), future.startedAt().get());
    }

    @Test
    void getNowReturnsCachedResultWhenItsAvailable() {
        final String futureResult = "future";
        final String cachedResult = "cached";
        ThrottledFuture<String> future = ThrottledFuture.of(() -> futureResult).setCachedResult(cachedResult);

        Optional<String> result = future.getNow();
        assertTrue(result.isPresent());
        assertEquals(cachedResult, result.get());
    }

    @Test
    void getNowReturnsEmptyWhenCacheIsNotSet() {
        final String futureResult = "future";
        ThrottledFuture<String> future = ThrottledFuture.of(() -> futureResult);

        Optional<String> result = future.getNow();
        assertTrue(result.isEmpty());
    }

    @Test
    void getNowReturnsEmptyWhenNullCacheIsSet() {
        final String futureResult = "future";
        ThrottledFuture<String> future = ThrottledFuture.of(() -> futureResult).setCachedResult(null);

        Optional<String> result = future.getNow();
        assertTrue(result.isEmpty());
    }

    @Test
    void getNowReturnsFutureResultWhenItsDoneAndNotCancelled() {
        final String futureResult = "future";
        final String cachedResult = "cached";
        ThrottledFuture<String> future = ThrottledFuture.of(() -> futureResult).setCachedResult(cachedResult);
        future.run(null);

        Optional<String> result = future.getNow();
        assertTrue(result.isPresent());
        assertEquals(futureResult, result.get());
    }

    @Test
    void getNowReturnsCachedResultWhenFutureIsCancelled() {
        final String futureResult = "future";
        final String cachedResult = "cached";
        ThrottledFuture<String> future = ThrottledFuture.of(() -> futureResult).setCachedResult(cachedResult);
        future.cancel(false);

        Optional<String> result = future.getNow();
        assertTrue(result.isPresent());
        assertEquals(cachedResult, result.get());
    }

    @Test
    void onCompletionCallbacksAreNotExecutedWhenTaskIsNull() {
        final AtomicBoolean callbackExecuted = new AtomicBoolean(false);
        final ThrottledFuture<?> future = new ThrottledFuture<>();
        future.then(ignored -> callbackExecuted.set(true));
        future.run(null);
        assertFalse(callbackExecuted.get());
    }

    @Test
    void transferUpdatesSecondFutureWithTask() {
        final Supplier<Void> firstTask = () -> null;
        final ThrottledFuture<Void> firstFuture = ThrottledFuture.of(firstTask);
        final ThrottledFuture<Void> secondFuture = mock(ThrottledFuture.class);

        firstFuture.transfer(secondFuture);

        verify(secondFuture).update(eq(firstTask), anyList());

        // now verifies that the task in the first future is null
        Object task = ReflectionTestUtils.getField(firstFuture, "task");
        assertNull(task);
        assertTrue(firstFuture.isCancelled());
    }

    @Test
    void transferUpdatesSecondFutureWithCallbacks() {
        final Consumer<ThrottledFuture<String>> firstCallback = (result) -> {};
        final Consumer<ThrottledFuture<String>> secondCallback = (result) -> {};
        final ThrottledFuture<String> firstFuture = ThrottledFuture.of(()->"").then(firstCallback);
        final ThrottledFuture<String> secondFuture = ThrottledFuture.of(()->"").then(secondCallback);
        final ThrottledFuture<String> mocked = mock(ThrottledFuture.class);
        final List<Consumer<String>> captured = new ArrayList<>(2);

        when(mocked.update(any(), any())).then(invocation -> {
            captured.addAll(invocation.getArgument(1, List.class));
            return mocked;
        });

        firstFuture.transfer(secondFuture);
        secondFuture.transfer(mocked);

        verify(mocked).update(notNull(), notNull());
        assertEquals(2, captured.size());
        assertTrue(captured.contains(firstCallback));
        // verifies that callbacks are added to the current ones and do not replace them
        assertTrue(captured.contains(secondCallback));
    }

    @Test
    void callbacksAreClearedAfterTransferring() {
        final Consumer<ThrottledFuture<String>> firstCallback = (result) -> {};
        final Consumer<ThrottledFuture<String>> secondCallback = (result) -> {};
        final ThrottledFuture<String> future = ThrottledFuture.of(()->"").then(firstCallback).then(secondCallback);
        final ThrottledFuture<String> mocked = mock(ThrottledFuture.class);

        future.transfer(mocked);

        final ArgumentCaptor<List<Consumer<ThrottledFuture<String>>>> captor = ArgumentCaptor.forClass(List.class);

        verify(mocked).update(notNull(), captor.capture());
        // captor takes the original list from the future
        // which is cleared afterward
        assertTrue(captor.getValue().isEmpty());
    }

    @Test
    void transferReturnsTargetWhenFutureIsRunning() {
        final ThrottledFuture<String> future = spy(ThrottledFuture.of(()->""));
        final ThrottledFuture<String> target = ThrottledFuture.of(()->"");
        when(future.isRunning()).thenReturn(true);
        doCallRealMethod().when(future).transfer(any());

        final ThrottledFuture<String> result = future.transfer(target);
        assertEquals(target, result);
    }

    @Test
    void transferReturnsTargetWhenFutureIsDone() {
        final ThrottledFuture<String> future = ThrottledFuture.done("");
        final ThrottledFuture<String> target = ThrottledFuture.of(()->"");

        final ThrottledFuture<String> result = future.transfer(target);
        assertEquals(target, result);
    }

    @Test
    void transferReturnsTargetWhenLockIsNotLockedForTransfer() throws Throwable {
        final ReentrantLock futureLock = new ReentrantLock();
        final ThrottledFuture<String> future = ThrottledFuture.of(()->"");
        final ThrottledFuture<String> target = ThrottledFuture.of(()->"");

        final Thread thread = new Thread(futureLock::lock);
        thread.start();
        thread.join();

        ReflectionTestUtils.setField(future, "lock", futureLock);

        final ThrottledFuture<String> result = future.transfer(target);
        assertEquals(target, result);
    }

    @Test
    void updateSetsTask() {
        final Supplier<String> task = ()->"";
        final ThrottledFuture<String> future = ThrottledFuture.of(() -> "");

        future.update(task, List.of());

        assertEquals(task, ReflectionTestUtils.getField(future, "task"));
    }

    @Test
    void updateAddsCallbacksToTheCurrentOnes() {
        final Consumer<ThrottledFuture<String>> callback = result -> {};
        final Consumer<ThrottledFuture<String>> originalCallback = result -> {};
        final ThrottledFuture<String> future = ThrottledFuture.of(() -> "").then(originalCallback);

        future.update(()->"", List.of(callback));

        final Collection<Consumer<String>> callbacks =
                (Collection<Consumer<String>>) ReflectionTestUtils.getField(future, "onCompletion");

        assertNotNull(callbacks);
        assertEquals(2, callbacks.size());
        assertTrue(callbacks.contains(originalCallback));
        assertTrue(callbacks.contains(callback));
    }

    @Test
    void updateReturnsNewFutureWhenFutureIsRunning() {
        final ThrottledFuture<String> future = spy(ThrottledFuture.of(()->""));
        when(future.isRunning()).thenReturn(true);
        doCallRealMethod().when(future).update(any(), any());

        final ThrottledFuture<String> result = future.update(()->"", List.of());
        assertNotEquals(future, result);
    }

    @Test
    void updateReturnsSelfWhenFutureIsNotRunningAndNotDone() {
        final ThrottledFuture<String> future = ThrottledFuture.of(()->"");

        final ThrottledFuture<String> result = future.update(()->"", List.of());
        assertEquals(future, result);
    }

    @Test
    void updateReturnsNewFutureWhenFutureIsDone() {
        final ThrottledFuture<String> future = ThrottledFuture.done("");

        final ThrottledFuture<String> result = future.update(()->"", List.of());
        assertNotEquals(future, result);
    }

    @Test
    void updateReturnsNewFutureWhenLockIsNotLockedForUpdate() throws Throwable {
        final ReentrantLock futureLock = new ReentrantLock();
        final ThrottledFuture<String> future = ThrottledFuture.of(()->"");

        final Thread thread = new Thread(futureLock::lock);
        thread.start();
        thread.join();

        ReflectionTestUtils.setField(future, "lock", futureLock);

        final ThrottledFuture<String> result = future.update(()->"", List.of());
        assertNotEquals(future, result);
    }

    @Test
    void runExecutionCallbackIsExecutedAfterStartedAtIsSetAndBeforeTaskExecution() {
        final AtomicBoolean taskExecuted = new AtomicBoolean(false);
        final ThrottledFuture<Void> future = ThrottledFuture.of(()->{
            taskExecuted.set(true);
        });

        future.run(f -> {
            assertEquals(future, f);
            assertTrue(f.startedAt().isPresent());
        });

        assertTrue(taskExecuted.get());
    }
}
