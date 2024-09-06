package cz.cvut.kbss.termit.util.throttle;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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
}
