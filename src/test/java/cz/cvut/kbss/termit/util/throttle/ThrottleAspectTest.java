package cz.cvut.kbss.termit.util.throttle;

import com.vladsch.flexmark.util.collection.OrderedMap;
import cz.cvut.kbss.termit.exception.TermItException;
import cz.cvut.kbss.termit.exception.ThrottleAspectException;
import cz.cvut.kbss.termit.util.longrunning.LongRunningTask;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.expression.spel.SpelParseException;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.TaskUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static cz.cvut.kbss.termit.util.Constants.THROTTLE_DISCARD_THRESHOLD;
import static cz.cvut.kbss.termit.util.Constants.THROTTLE_THRESHOLD;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

//@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ThrottleAspectTest {

    /**
     * Throttled futures from {@link #sut}
     */
    OrderedMap<ThrottleAspect.Identifier, ThrottledFuture<Object>> throttledFutures;

    /**
     * Last run map from {@link #sut}
     */
    OrderedMap<ThrottleAspect.Identifier, Instant> lastRun;

    /**
     * Scheduled futures from {@link #sut}
     */
    NavigableMap<ThrottleAspect.Identifier, Future<Object>> scheduledFutures;

    /**
     * Mocked task scheduler.
     * Does not execute tasks automatically,
     * they need to be executed with {@link #executeScheduledTasks()}
     * Tasks are wrapped into {@link FutureTask} and saved to {@link #taskSchedulerTasks}.
     */
    TaskScheduler taskScheduler;

    SynchronousTransactionExecutor transactionExecutor;

    /**
     * Tasks that were submitted to {@link #taskScheduler}
     * @see #beforeEach()
     */
    OrderedMap<Runnable, Instant> taskSchedulerTasks;

    ThrottleAspect sut;

    MockedThrottle throttleA;

    MockedThrottle throttleB;

    MockedThrottle throttleC;

    /**
     * Default mock:<br>
     * return type: primitive {@code void}<br>
     * parameters: {@link Object Object paramA}, {@link Object Object paramB}<br>
     */
    MockedMethodSignature signatureA;

    /**
     * Default mock:<br>
     * return type: wrapped {@link Void}<br>
     * parameters: {@link Map Map&lt;String,String&gt; paramName}<br>
     */
    MockedMethodSignature signatureB;

    /**
     * Default mock:<br>
     * return type: primitive {@code void}<br>
     * parameters: {@link Object Object paramA}, {@link Object Object paramB}<br>
     */
    MockedMethodSignature signatureC;

    /**
     * Default mock: returning {@code null} on proceed,
     * method called with two {@link Object} arguments
     * @see #signatureA
     */
    ProceedingJoinPoint joinPointA;

    /**
     * Default mock: returning {@code null} on proceed,
     * method called with one parameter ({@link Map Map&lt;String,String&gt;} with two entries)
     * @see #signatureB
     */
    ProceedingJoinPoint joinPointB;

    /**
     * Default mock: returning {@code null} on proceed,
     * method called with two {@link Object} parameters
     * @see #signatureC
     */
    ProceedingJoinPoint joinPointC;

    Clock clock = Clock.fixed(Instant.now(), ZoneId.of("UTC"));

    void mockA() throws Throwable {
        joinPointA = mock(ProceedingJoinPoint.class);
        when(joinPointA.proceed()).thenReturn(null);
        signatureA = spy(new MockedMethodSignature("methodA", Void.TYPE, new Class[]{Object.class, Object.class}, new String[]{
                "paramA", "paramB"}));
        when(joinPointA.getSignature()).thenReturn(signatureA);
        when(joinPointA.getArgs()).thenReturn(new Object[]{new Object(), new Object()});
        when(joinPointA.getTarget()).thenReturn(this);

        throttleA = new MockedThrottle("'string literal'", "'my.testing.group.A'");
    }

    void mockB() throws Throwable {
        joinPointB = mock(ProceedingJoinPoint.class);
        when(joinPointB.proceed()).thenReturn(null);
        signatureB = spy(new MockedMethodSignature("methodB", Void.class, new Class[]{Map.class}, new String[]{"paramName"}));
        when(joinPointB.getSignature()).thenReturn(signatureB);

        when(joinPointB.getArgs()).thenReturn(new Object[]{Map.of("first", "firstValue", "second", "secondValue")});
        when(joinPointB.getTarget()).thenReturn(this);

        throttleB = new MockedThrottle("{#paramName.get('second'), #paramName.get('first')}", "'my.testing.group.B'");
    }

    void mockC() throws Throwable {
        joinPointC = mock(ProceedingJoinPoint.class);
        when(joinPointC.proceed()).thenReturn(null);
        signatureC = spy(new MockedMethodSignature("methodC", Void.TYPE, new Class[]{Object.class, Object.class}, new String[]{
                "paramC", "paramD"}));
        when(joinPointC.getSignature()).thenReturn(signatureC);
        when(joinPointC.getArgs()).thenReturn(new Object[]{new Object(), new Object()});
        when(joinPointC.getTarget()).thenReturn(this);

        throttleC = new MockedThrottle("'string literal'", "'my.testing'");
    }

    @BeforeEach
    void beforeEach() throws Throwable {
        mockA();
        mockB();
        mockC();

        taskSchedulerTasks = new OrderedMap<>();

        taskScheduler = mock(TaskScheduler.class);

        when(taskScheduler.schedule(any(Runnable.class), any(Instant.class))).then(invocation -> {
            final Runnable decorated = TaskUtils.decorateTaskWithErrorHandler(invocation.getArgument(0, Runnable.class), null, false);
            final ScheduledFutureTask<Object> task = new ScheduledFutureTask<>(Executors.callable(decorated));
            taskSchedulerTasks.put(task, invocation.getArgument(1, Instant.class));
            System.out.println("Scheduled task at " + invocation.getArgument(1, Instant.class));
            return task;
        });

        throttledFutures = new OrderedMap<>();
        lastRun = new OrderedMap<>();
        scheduledFutures = new TreeMap<>();

        Clock mockedClock = mock(Clock.class);
        when(mockedClock.instant()).then(invocation -> getInstant());

        transactionExecutor = spy(SynchronousTransactionExecutor.class);

        sut = new ThrottleAspect(throttledFutures, lastRun, scheduledFutures, taskScheduler, mockedClock, transactionExecutor);
    }

    /**
     * @return current timestamp based on mocked {@link #clock}
     */
    Instant getInstant() {
        return clock.instant().truncatedTo(ChronoUnit.SECONDS);
    }

    void addSecond() {
        clock = Clock.fixed(clock.instant().plusSeconds(1), ZoneId.of("UTC"));
    }

    void skipThreshold() {
        clock = Clock.fixed(clock.instant().plus(THROTTLE_THRESHOLD), ZoneId.of("UTC"));
    }

    void skipDiscardThreshold() {
        clock = Clock.fixed(clock.instant()
                                 .plus(THROTTLE_DISCARD_THRESHOLD)
                                 .plus(THROTTLE_THRESHOLD)
                                 .plusSeconds(1),
                ZoneId.of("UTC"));
    }

    /**
     * Executes all tasks in {@link #taskSchedulerTasks} and clears the map.
     */
    void executeScheduledTasks() {
        taskSchedulerTasks.forEach((runnable, instant) -> runnable.run());
        taskSchedulerTasks.clear();
    }

    /**
     * If a task was executed more than a threshold period before, it should NOT be debounced
     */
    @Test
    void firstCallAfterThresholdIsScheduledImmediately() throws Throwable {
        sut.throttleMethodCall(joinPointA, throttleA); // first call of the method
        executeScheduledTasks(); // simulate that the task was executed

        // simulate that there was a delay before next call,
        // and it was greater than threshold
        skipThreshold();
        addSecond();

        final Instant expectedTime = getInstant(); // note current time
        sut.throttleMethodCall(joinPointA, throttleA); // second call of the method

        // verify that the task from the second call was scheduled immediately
        // because the last time, task was executed was before more than the threshold period
        assertEquals(1, taskSchedulerTasks.size());
        assertEquals(expectedTime, taskSchedulerTasks.getValue(0));
    }

    /**
     * Calling the annotated method three times
     * will execute it only once with the newest data.
     * Task is scheduled by the first call.
     */
    @Test
    void threeImmediateCallsScheduleFirstCallWithLastTask() throws Throwable {
        // define a future as the return type of the method
        signatureA.setReturnType(Future.class);

        final Supplier<String> methodResult = () -> "method result";
        final Supplier<String> anotherMethodResult = () -> "another method result";

        final ThrottledFuture<String> methodFuture = ThrottledFuture.of(methodResult);

        // for each method call, make new future
        doAnswer(invocation -> ThrottledFuture.of(anotherMethodResult)).when(joinPointA).proceed();

        final Instant firstCall = getInstant();
        // simulate first call
        sut.throttleMethodCall(joinPointA, throttleA);

        addSecond();
        // simulate second call
        sut.throttleMethodCall(joinPointA, throttleA); // both tasks should return anotherMethodResult
        addSecond();

        // change the return value of the method to the prepared future
        doReturn(methodFuture).when(joinPointA).proceed();

        // simulate last call
        sut.throttleMethodCall(joinPointA, throttleA); // should return methodResult

        // there should be only a single scheduled future
        // threshold was not reached and no task was executed, calls should be merged
        // scheduled for immediate execution from the first call with the newest data from the last call
        assertEquals(1, scheduledFutures.size());
        assertEquals(1, taskSchedulerTasks.size());
        assertEquals(1, throttledFutures.size());

        final Instant scheduledAt = taskSchedulerTasks.getValue(0);
        final Runnable scheduledTask = taskSchedulerTasks.getKey(0);
        assertNotNull(scheduledAt);
        assertNotNull(scheduledTask);
        // the task should be scheduled at the first call
        assertEquals(firstCall, scheduledAt);

        final ThrottledFuture<Object> future = throttledFutures.getValue(0);
        assertNotNull(future);

        // perform task execution
        executeScheduledTasks();
        // the future should be completed
        assertTrue(future.isDone());
        // check that the task in the future is from the last method call
        assertEquals(methodResult.get(), future.get());
    }

    /**
     * When method is called in the throttle interval
     * calls are merged and method will be executed only once.
     * Ensures that both futures returned from method calls are same.
     */
    @Test
    void callsInThrottleIntervalAreMerged() throws Throwable {
        final String[] params = new String[]{"param1", "param2", "param3", "param4", "param5", "param6"};
        // define a future as the return type of the method
        signatureA.setReturnType(Future.class);

        // for each method call, make new future with "another method task"
        doAnswer(invocation -> new ThrottledFuture<String>()).when(joinPointA).proceed();

        // simulate first call
        when(joinPointA.getArgs()).thenReturn(new Object[]{params[0], params[1]});
        final Object result1 = sut.throttleMethodCall(joinPointA, throttleA);

        addSecond();
        // simulate second call
        when(joinPointA.getArgs()).thenReturn(new Object[]{params[2], params[3]});
        final Object result2 = sut.throttleMethodCall(joinPointA, throttleA);

        // both calls returned the same future
        // this ensures that calls are actually merged and a single result satisfies all merged calls
        assertInstanceOf(Future.class, result1);
        assertInstanceOf(Future.class, result2);
        assertEquals(result1, result2);
    }

    /**
     * Within the threshold interval, when a task from first call is already executed, during a new call,
     * new future is scheduled.
     */
    @Test
    @SuppressWarnings("unchecked")
    void schedulesNewFutureWhenTheOldOneIsCompletedDuringThreshold() throws Throwable {
        // set return type as future
        signatureA.setReturnType(Future.class);
        // return new throttled future on each method call
        when(joinPointA.proceed()).then(invocation -> ThrottledFuture.of(() -> "result"));

        // first call of the method
        ThrottledFuture<Object> firstFuture = (ThrottledFuture<Object>) sut.throttleMethodCall(joinPointA, throttleA);
        addSecond(); // changing time (but not more than the threshold)

        // verify that a future was returned
        assertNotNull(firstFuture);
        // verify that the future is pending
        assertFalse(firstFuture.isDone());
        assertFalse(firstFuture.isCancelled());
        assertFalse(firstFuture.isRunning());

        // verify that a single task was scheduled
        assertEquals(1, taskSchedulerTasks.size());
        // execute the task
        executeScheduledTasks();
        // verify that the task was completed
        assertTrue(firstFuture.isDone());
        assertFalse(firstFuture.isCancelled());
        assertFalse(firstFuture.isRunning());

        // perform a second call, throttled interval was not reached
        ThrottledFuture<Object> secondFuture = (ThrottledFuture<Object>) sut.throttleMethodCall(joinPointA, throttleA);
        addSecond();

        // verify returned second future
        assertNotNull(secondFuture);

        // verify that returned futures are not same
        assertNotEquals(firstFuture, secondFuture);

        // it was not completed yet
        assertFalse(secondFuture.isDone());
        assertFalse(secondFuture.isCancelled());
        assertFalse(secondFuture.isRunning());

        // verify a new future was scheduled
        assertEquals(1, scheduledFutures.size());
        assertEquals(1, taskSchedulerTasks.size());
        // execute new task
        executeScheduledTasks();

        // the new future was completed
        assertTrue(secondFuture.isDone());
        assertFalse(secondFuture.isCancelled());
        assertFalse(secondFuture.isRunning());
    }

    /**
     * Ensures that calling the annotated method even outside the threshold
     * merges calls when no future was resolved yet (and task is not running).
     */
    @SuppressWarnings("unchecked")
    @Test
    void callsAreMergedWhenCalledOutsideTheThresholdButNoFutureExecutedYet() throws Throwable {
        // change return type to future
        signatureA.setReturnType(Future.class);

        final String firstResult = "first result";
        final String secondResult = "second result";

        // on each method call return a new throttled future with firstResult
        when(joinPointA.proceed()).then(invocation -> ThrottledFuture.of(() -> firstResult));

        // first method call
        Future<Object> firstFuture = (Future<Object>) sut.throttleMethodCall(joinPointA, throttleA);

        // ensure that threshold was reached
        skipThreshold();
        addSecond();

        // change method call result to throttled future with secondResult
        when(joinPointA.proceed()).then(invocation -> ThrottledFuture.of(() -> secondResult));

        // second method call
        Future<Object> secondFuture = (Future<Object>) sut.throttleMethodCall(joinPointA, throttleA);

        // verify that the returned future is not null and was not completed yet
        assertNotNull(firstFuture);
        assertFalse(firstFuture.isDone());
        assertFalse(firstFuture.isCancelled());

        // verify that calls were merged and returned futures are same
        assertEquals(firstFuture, secondFuture);

        // only one task was scheduled
        assertEquals(1, scheduledFutures.size());
        assertEquals(1, taskSchedulerTasks.size());

        executeScheduledTasks();

        assertTrue(firstFuture.isDone());
        assertFalse(firstFuture.isCancelled());

        // verify that the future was resolved with the newest call data
        assertEquals(secondResult, firstFuture.get());

        assertTrue(firstFuture.isDone());
        assertFalse(firstFuture.isCancelled());
        assertEquals(secondResult, firstFuture.get());
    }

    /**
     * When task is currently being executed and last execution did not happen in last threshold period,
     * next call should not be executed immediately.
     * That is because there is already same task running and so a new call should be debounced.
     */
    @Test
    void callToAMethodDuringTaskExecutionOutsideOfThresholdWillResolveToScheduleOfNewFuture() throws Throwable {
        AtomicBoolean allowTaskToFinish = new AtomicBoolean(false);
        AtomicBoolean taskRunning = new AtomicBoolean(false);

        // method return type is void, whole method body is considered as a task
        when(joinPointA.proceed()).then(invocation -> {
            // simulate long running task
            taskRunning.set(true);
            while (!allowTaskToFinish.get()) {
                Thread.yield();
            }
            return null;
        });

        // first method call
        // there was no call before, which means the task should be scheduled for immediate execution
        sut.throttleMethodCall(joinPointA, throttleA);

        Thread taskThread = new Thread(taskSchedulerTasks.getKey(0));

        try {
            // start long task execution
            taskThread.start();

            await("task execution start").atMost(Duration.ofSeconds(30)).untilTrue(taskRunning);

            assertEquals(1, throttledFutures.size());
            assertTrue(throttledFutures.getValue(0).isRunning());
            final Map.Entry<Runnable, Instant> immediateSchedule = taskSchedulerTasks.entrySet().getValue(0);

            assertNotNull(immediateSchedule);
            // verify that the task was scheduled immediately
            assertEquals(getInstant(), immediateSchedule.getValue());

            // move time by a second
            addSecond();

            // this is second method call in the throttled threshold (time moved only by a second)
            sut.throttleMethodCall(joinPointA, throttleA);
            // task should not be scheduled for immediate execution
            // verify a new task was scheduled
            assertEquals(2, taskSchedulerTasks.size());

            final Map.Entry<Runnable, Instant> scheduled = taskSchedulerTasks.entrySet().getValue(1);
            assertNotEquals(immediateSchedule, scheduled);

            // the second task should be debounced by a throttle threshold
            final Instant expectedSchedule = immediateSchedule.getValue().plusSeconds(1) // added second to the clock
                                                              .plus(THROTTLE_THRESHOLD); // should be debounced
            assertEquals(expectedSchedule, scheduled.getValue());
        } finally { // ensure that the thread will be terminated in the test
            allowTaskToFinish.set(true);
            taskThread.join(60 * 1000); /* one minute, ensures that the test won't run indefinitely*/
            if (taskThread.isAlive()) {
                taskThread.interrupt();
                fail("task thread thread interrupted due to timeout");
            }
        }
    }

    @Test
    void cancelsAllScheduledFuturesWhenNewTaskWithLowerGroupIsScheduled() throws Throwable {
        throttleA.setGroup("'the.group.identifier.first'");
        throttleB.setGroup("'the.group.identifier.second'");
        throttleC.setGroup("'the.group.identifier'");

        sut.throttleMethodCall(joinPointA, throttleA);
        sut.throttleMethodCall(joinPointB, throttleB);

        final Map<ThrottleAspect.Identifier, Future<Object>> futures = Map.copyOf(scheduledFutures);

        assertEquals(2, throttledFutures.size());
        assertEquals(2, scheduledFutures.size());
        assertEquals(2, taskSchedulerTasks.size());

        sut.throttleMethodCall(joinPointC, throttleC);

        assertEquals(1, throttledFutures.size());
        assertEquals(1, scheduledFutures.size());
        assertEquals(3, taskSchedulerTasks.size());

        assertEquals(2, futures.size());
        futures.forEach((k, f) -> assertTrue(f.isCancelled()));
    }

    @Test
    void immediatelyCancelsNewFutureWhenLowerGroupIsAlreadyScheduled() throws Throwable {
        throttleA.setGroup("'the.group.identifier'");
        throttleB.setGroup("'the.group.identifier.with.higher.value'");

        signatureB.setReturnType(Future.class);
        when(joinPointB.proceed()).then(invocation -> new ThrottledFuture<>());

        sut.throttleMethodCall(joinPointA, throttleA);

        final Map<ThrottleAspect.Identifier, Future<Object>> futures = Map.copyOf(scheduledFutures);

        Object result = sut.throttleMethodCall(joinPointB, throttleB);
        assertNotNull(result);
        assertInstanceOf(ThrottledFuture.class, result);
        Future<?> secondCall = (Future<?>) result;

        assertEquals(1, scheduledFutures.size());

        final Future<Object> oldFuture = futures.values().iterator().next();
        final Future<Object> currentFuture = scheduledFutures.values().iterator().next();
        assertEquals(oldFuture, currentFuture);
        assertFalse(currentFuture.isDone());
        assertFalse(currentFuture.isCancelled());

        assertTrue(secondCall.isCancelled());
    }

    /**
     * When a thread is executing a task from throttled method, and it reaches another throttled method,
     * no further task should be scheduled and the throttled method should be executed synchronously.
     */
    @Test
    void callToThrottledMethodReturningVoidFromAlreadyThrottledThreadResultsInSynchronousExecution() throws Throwable {
        AtomicLong threadId = new AtomicLong(-1);

        // prepare a simulated nested throttled method
        when(joinPointB.proceed()).then(invocation -> {
            threadId.set(Thread.currentThread().getId());
            return null; // void return type
        });

        // when method A is executed, call throttled method B
        when(joinPointA.proceed()).then(invocation -> {
            sut.throttleMethodCall(joinPointB, throttleB);
            return null; // void return type
        });

        sut.throttleMethodCall(joinPointA, throttleA);

        // execute a single scheduled task
        Thread runThread = new Thread(taskSchedulerTasks.getKey(0));
        runThread.start();

        runThread.join(15 * 1000);
        if (runThread.isAlive()) {
            runThread.interrupt();
            fail("task thread thread interrupted due to timeout");
        }

        assertNotEquals(-1, threadId.get());
        assertEquals(runThread.getId(), threadId.get());
    }

    /**
     * Same as {@link #callToThrottledMethodReturningVoidFromAlreadyThrottledThreadResultsInSynchronousExecution}
     * but with method returning a future
     */
    @Test
    void callToThrottledMethodReturningFutureFromAlreadyThrottledThreadResultsInSynchronousExecution() throws Throwable {
        AtomicLong threadId = new AtomicLong(-1);

        signatureA.setReturnType(Future.class);
        signatureB.setReturnType(Future.class);

        // prepare a simulated nested throttled method
        when(joinPointB.proceed()).then(invocation -> ThrottledFuture.of(()->
            threadId.set(Thread.currentThread().getId())
        ));

        // when method A is executed, call throttled method B
        when(joinPointA.proceed()).then(invocation -> ThrottledFuture.of(() -> {
            try {
                sut.throttleMethodCall(joinPointB, throttleB);
            } catch (Throwable t) {
                fail(t);
            }
        }));

        sut.throttleMethodCall(joinPointA, throttleA);

        // execute a single scheduled task
        Thread runThread = new Thread(taskSchedulerTasks.getKey(0));
        runThread.start();

        runThread.join(15 * 1000);
        if (runThread.isAlive()) {
            runThread.interrupt();
            fail("task thread thread interrupted due to timeout");
        }

        assertNotEquals(-1, threadId.get());
        assertEquals(runThread.getId(), threadId.get());
    }

    /**
     * When a throttled method is annotated with {@link Transactional @Transactional}
     * the asynchronous task should be executed with {@link SynchronousTransactionExecutor}
     * by a throttled thread.
     */
    @Test
    void taskFromMethodAnnotatedWithTransactionalIsExecutedWithTransactionExecutor() throws Throwable {
        // simulates a method object with transactional annotation
        when(signatureA.getMethod()).thenReturn(SynchronousTransactionExecutor.class.getDeclaredMethod("execute", Runnable.class));
        signatureA.setReturnType(Future.class);
        Runnable task = () -> {};
        when(joinPointA.proceed()).thenReturn(ThrottledFuture.of(task));

        ThrottledFuture<String> result = (ThrottledFuture<String>) sut.throttleMethodCall(joinPointA, throttleA);

        assertNotNull(result);
        verifyNoInteractions(transactionExecutor);

        executeScheduledTasks();

        verify(transactionExecutor).execute(any());
    }

    /**
     * When a task is executed, all three maps are cleared from
     * entries older than {@link cz.cvut.kbss.termit.util.Constants#THROTTLE_DISCARD_THRESHOLD THROTTLE_DISCARD_THRESHOLD}
     * plus {@link cz.cvut.kbss.termit.util.Constants#THROTTLE_THRESHOLD THROTTLE_THRESHOLD}
     */
    @Test
    void allMapsAreClearedAfterDiscardThreshold() throws Throwable {
        sut.throttleMethodCall(joinPointA, throttleA);
        sut.throttleMethodCall(joinPointB, throttleB);
        sut.throttleMethodCall(joinPointC, throttleC);
        skipThreshold();
        executeScheduledTasks();
        sut.throttleMethodCall(joinPointA, throttleA);
        sut.throttleMethodCall(joinPointB, throttleB);
        sut.throttleMethodCall(joinPointC, throttleC);
        executeScheduledTasks();
        skipThreshold();
        sut.throttleMethodCall(joinPointA, throttleA);
        sut.throttleMethodCall(joinPointB, throttleB);
        sut.throttleMethodCall(joinPointC, throttleC);
        addSecond();
        executeScheduledTasks();

        // skip discard threshold
        skipDiscardThreshold();
        sut.throttleMethodCall(joinPointA, throttleA);

        executeScheduledTasks();

        // only single task left (the last one, which cleared the maps)
        assertEquals(1, scheduledFutures.size());
        assertEquals(1, throttledFutures.size());
        assertEquals(1, lastRun.size());
    }


    @Test
    void aspectDoesNotThrowWhenMethodReturnsUnboxedVoidBySignature() throws Throwable {
        signatureA.setReturnType(Void.TYPE);
        when(joinPointA.proceed()).thenReturn(null);

        assertDoesNotThrow(() -> sut.throttleMethodCall(joinPointA, throttleA));
    }

    @Test
    void aspectDoesNotThrowWhenMethodReturnsBoxedVoidBySignature() throws Throwable {
        signatureA.setReturnType(Void.class);
        when(joinPointA.proceed()).thenReturn(null);

        assertDoesNotThrow(() -> sut.throttleMethodCall(joinPointA, throttleA));
    }

    @Test
    void aspectDoesNotThrowWhenMethodReturnsFutureBySignature() throws Throwable {
        signatureA.setReturnType(Future.class);
        when(joinPointA.proceed()).then(invocation -> new ThrottledFuture<>());

        assertDoesNotThrow(() -> sut.throttleMethodCall(joinPointA, throttleA));
    }

    @Test
    void aspectDoesNotThrowWhenMethodReturnsThrottledFutureBySignature() throws Throwable {
        signatureA.setReturnType(ThrottledFuture.class);
        when(joinPointA.proceed()).then(invocation -> new ThrottledFuture<>());

        assertDoesNotThrow(() -> sut.throttleMethodCall(joinPointA, throttleA));
    }

    @ParameterizedTest
    // just few sample classes
    @ValueSource(classes = {String.class, Integer.class, Optional.class, FutureTask.class, CompletableFuture.class})
    void aspectThrowsWhenMethodNotReturnsVoidOrFutureBySignature(Class<?> returnType) throws Throwable {
        signatureA.setReturnType(returnType);
        when(joinPointA.proceed()).thenReturn(new Object());

        assertThrows(ThrottleAspectException.class, () -> sut.throttleMethodCall(joinPointA, throttleA));
    }

    @Test
    void aspectDoesThrowWhenMethodReturnsNullByValueAndFutureBySignature() throws Throwable {
        signatureA.setReturnType(Future.class);
        when(joinPointA.proceed()).thenReturn(null);

        assertThrows(ThrottleAspectException.class, () -> sut.throttleMethodCall(joinPointA, throttleA));
    }

    @ParameterizedTest
    @ValueSource(classes = {Future.class, ThrottledFuture.class})
    void aspectThrowsWhenMethodDoesNotReturnsThrottledFutureObject(Class<?> returnType) throws Throwable {
        signatureA.setReturnType(returnType);
        when(joinPointA.proceed()).thenReturn(new FutureTask<>(() -> ""));

        assertThrows(ThrottleAspectException.class, () -> sut.throttleMethodCall(joinPointA, throttleA));
    }

    @ParameterizedTest
    @ValueSource(classes = {Future.class, ThrottledFuture.class})
    void aspectThrowsWhenMethodReturnsNullWithFutureBySignature(Class<?> returnType) throws Throwable {
        signatureA.setReturnType(returnType);
        when(joinPointA.proceed()).thenReturn(null);

        assertThrows(ThrottleAspectException.class, () -> sut.throttleMethodCall(joinPointA, throttleA));
    }

    @Test
    void aspectResolvesThrottleGroupProviderClassInSpEL() throws Throwable {
        throttleA.setGroup("T(ThrottleGroupProvider).getTextAnalysisVocabulariesAll()");

        sut.throttleMethodCall(joinPointA, throttleA);

        final String expectedGroup = ThrottleGroupProvider.getTextAnalysisVocabulariesAll();
        final String resolvedGroup = scheduledFutures.firstEntry().getKey().getGroup();
        assertEquals(expectedGroup, resolvedGroup);
    }

    @Test
    void exceptionPropagatedWhenJoinPointProceedThrows() throws Throwable {
        when(joinPointA.proceed()).thenThrow(new RuntimeException());

        sut.throttleMethodCall(joinPointA, throttleA);

        assertDoesNotThrow(() -> taskSchedulerTasks.forEach((r, i) -> r.run()));
    }

    @Test
    void exceptionPropagatedFromFutureTask() throws Throwable {
        final String exceptionMessage = "termit exception";
        when(joinPointA.proceed()).then(invocation -> new ThrottledFuture<>().update(() -> {
            throw new TermItException(exceptionMessage);
        }, List.of()));
        signatureA.setReturnType(Future.class);

        sut.throttleMethodCall(joinPointA, throttleA);

        assertEquals(1, taskSchedulerTasks.size());
        assertEquals(1, scheduledFutures.size());
        Runnable scheduled = taskSchedulerTasks.getKey(0);
        Future<?> future = scheduledFutures.firstEntry().getValue();

        assertNotNull(scheduled);
        assertNotNull(future);
        assertDoesNotThrow(scheduled::run); // exception is thrown here, but future stores it
        // exception is then re-thrown during future#get()
        ExecutionException e = assertThrows(ExecutionException.class, future::get);
        assertEquals(exceptionMessage, e.getCause().getMessage());
    }

    @Test
    void resolvedFutureFromMethodIsReturnedWithoutSchedule() throws Throwable {
        signatureA.setReturnType(Future.class);
        final String result = "result of the method";
        when(joinPointA.proceed()).then(invocation -> ThrottledFuture.done(result));

        Future<String> future = (Future<String>) sut.throttleMethodCall(joinPointA, throttleA);

        assertNotNull(future);
        assertTrue(future.isDone());
        assertFalse(future.isCancelled());
        assertEquals(result, future.get());
    }

    @Test
    void getTasksReturnsCopyOfThrottledFutures() throws Throwable {
        sut.throttleMethodCall(joinPointA, throttleA);
        sut.throttleMethodCall(joinPointB, throttleB);
        sut.throttleMethodCall(joinPointC, throttleC);

        final Collection<LongRunningTask> tasks = sut.getTasks();
        // assert a COPY returned, so they are not the same
        assertNotSame(throttledFutures.values(), tasks);
        assertIterableEquals(throttledFutures.values(), tasks);
    }

    @Test
    void aspectThrowsOnMalformedSpel() {
        throttleA.setValue("invalid spel expression");
        assertThrows(SpelParseException.class, () -> sut.throttleMethodCall(joinPointA, throttleA));
    }

    @Test
    void aspectThrowsOnInvalidSpelParamReference() {
        throttleA.setValue("{#nonExistingParameter}");
        assertThrows(ThrottleAspectException.class, () -> sut.throttleMethodCall(joinPointA, throttleA));
    }

    @Test
    void aspectDoesNotThrowsOnStringLiteral() {
        throttleA.setValue("'valid spel'");
        assertDoesNotThrow(() -> sut.throttleMethodCall(joinPointA, throttleA));
    }

    @Test
    void aspectDoesNotThrowsOnEmptyIdentifier() {
        throttleA.setValue("");
        assertDoesNotThrow(() -> sut.throttleMethodCall(joinPointA, throttleA));
    }

    @Test
    void aspectDownNotThrowsOnEmptyGroup() {
        throttleA.setGroup("");
        assertDoesNotThrow(() -> sut.throttleMethodCall(joinPointA, throttleA));
    }

    @Test
    void aspectConstructsFromAutowiredConstructor() {
        assertDoesNotThrow(() -> new ThrottleAspect(taskScheduler, transactionExecutor));
    }

    @Test
    void futureWithHigherGroupIsNotCanceledWhenFutureWithLowerGroupIsCanceled() throws Throwable {
        // future C has lower group than future A and B
        sut.throttleMethodCall(joinPointC, throttleC);
        // cancel the scheduled future
        scheduledFutures.firstEntry().getValue().cancel(false);

        signatureA.setReturnType(Future.class);
        when(joinPointA.proceed()).thenReturn(ThrottledFuture.of(() -> null));

        Future<?> higherFuture = (Future<?>) sut.throttleMethodCall(joinPointA, throttleA);

        assertNotNull(higherFuture);
        assertFalse(higherFuture.isCancelled());
        assertEquals(2, scheduledFutures.size());
    }

    @Test
    void newScheduleWithBlankGroupDoesNotCancelsAnyOtherFuture() throws Throwable {
        throttleA.setGroup("");
        throttleC.setGroup("");

        sut.throttleMethodCall(joinPointA, throttleA); // blank group
        sut.throttleMethodCall(joinPointB, throttleB); // non blank group
        sut.throttleMethodCall(joinPointC, throttleC); // blank group
        // no future should be canceled

        assertEquals(3, throttledFutures.size());
        assertEquals(3, scheduledFutures.size());

        Stream.concat(throttledFutures.values().stream(), scheduledFutures.values().stream())
                .forEach(future -> assertFalse(future.isCancelled()));
    }

    @Test
    void mapsAreNotClearedWhenFutureIsNotDone() throws Throwable {
        sut.throttleMethodCall(joinPointA, throttleA); // blank group
        skipDiscardThreshold();
        sut.throttleMethodCall(joinPointB, throttleB); // non blank group

        taskSchedulerTasks.getKey(1).run();

        assertEquals(2, scheduledFutures.size());
        assertEquals(2, throttledFutures.size());
    }
}
