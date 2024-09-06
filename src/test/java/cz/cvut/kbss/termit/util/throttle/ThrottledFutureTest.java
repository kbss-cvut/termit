package cz.cvut.kbss.termit.util.throttle;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        final Object result = new Object();
        final ThrottledFuture<?> future = ThrottledFuture.of(() -> result);
        final AtomicBoolean completed = new AtomicBoolean(false);
        final AtomicReference<Object> futureResult = new AtomicReference<>(null);
        future.run();
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        future.then(fResult -> {
            completed.set(true);
            futureResult.set(fResult);
        });
        assertTrue(completed.get());
        assertEquals(result, futureResult.get());
    }

    @Test
    void thenActionIsNotExecutedWhenFutureIsAlreadyCancelled() {
        final ThrottledFuture<?> future = ThrottledFuture.of(Object::new);
        final AtomicBoolean completed = new AtomicBoolean(false);
        future.cancel(false);
        assertTrue(future.isCancelled());
        future.then(result -> completed.set(true));
        assertFalse(completed.get());
    }

    @Test
    void thenActionIsExecutedOnceFutureIsRun() {
        final Object result = new Object();
        final AtomicBoolean completed = new AtomicBoolean(false);
        final AtomicReference<Object> fResult = new AtomicReference<>(null);
        final ThrottledFuture<?> future = ThrottledFuture.of(() -> result);
        future.then(futureResult -> {
            completed.set(true);
            fResult.set(futureResult);
        });
        assertNull(fResult.get());
        assertFalse(completed.get()); // action was not executed yet
        future.run();
        assertTrue(completed.get());
        assertEquals(result, fResult.get());
    }

    @Test
    void thenActionIsNotExecutedOnceFutureIsCancelled() {
        final Object result = new Object();
        final AtomicBoolean completed = new AtomicBoolean(false);
        final ThrottledFuture<?> future = ThrottledFuture.of(() -> result);
        future.then(futureResult -> completed.set(true));
        assertFalse(completed.get()); // action was not executed yet
        future.cancel(false);
        assertFalse(completed.get());
    }

    @Test
    void callingRunWillExecuteFutureOnlyOnce() {
        AtomicInteger count = new AtomicInteger(0);
        final ThrottledFuture<?> future = ThrottledFuture.of(() -> {
            count.incrementAndGet();
        });

        future.run();
        final Optional<Instant> runningSince = future.startedAt();
        assertTrue(runningSince.isPresent());
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        assertFalse(future.isRunning());

        future.run();
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
        final Thread threadA = new Thread(future::run);
        final Thread threadB = new Thread(future::run);
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
}
