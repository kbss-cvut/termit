package cz.cvut.kbss.termit.util.throttle;

import com.vladsch.flexmark.util.collection.OrderedMap;
import cz.cvut.kbss.termit.exception.ThrottleAspectException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.AbstractMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.function.Supplier;

import static cz.cvut.kbss.termit.util.Constants.THROTTLE_THRESHOLD;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ThrottleAspectTest {

    OrderedMap<ThrottleAspect.Identifier, ThrottledFuture<Object>> throttledFutures;

    OrderedMap<ThrottleAspect.Identifier, Instant> lastRun;

    NavigableMap<ThrottleAspect.Identifier, Future<Object>> scheduledFutures;

    TaskScheduler taskScheduler;

    TransactionExecutor transactionExecutor;

    OrderedMap<Runnable, Instant> taskSchedulerTasks;

    ThrottleAspect sut;

    MockedThrottle throttleA;

    MockedThrottle throttleB;

    MockedThrottle throttleC;

    MockedMethodSignature signatureA;

    MockedMethodSignature signatureB;

    MockedMethodSignature signatureC;

    ProceedingJoinPoint joinPointA;

    ProceedingJoinPoint joinPointB;

    ProceedingJoinPoint joinPointC;

    Clock clock = Clock.fixed(Instant.now(), ZoneId.of("UTC"));

    void mockA() throws Throwable {
        joinPointA = mock(ProceedingJoinPoint.class);
        when(joinPointA.proceed()).thenReturn(null);
        signatureA = spy(new MockedMethodSignature(Void.TYPE, new Class[]{Object.class, Object.class}, new String[]{
                "paramA", "paramB"}));
        when(joinPointA.getSignature()).thenReturn(signatureA);
        when(joinPointA.getArgs()).thenReturn(new Object[]{new Object(), new Object()});
        when(joinPointA.getTarget()).thenReturn(this);

        throttleA = new MockedThrottle("'string literal'", "'my.testing.group.A'");
    }

    void mockB() throws Throwable {
        joinPointB = mock(ProceedingJoinPoint.class);
        when(joinPointB.proceed()).thenReturn(null);
        signatureB = spy(new MockedMethodSignature(Void.class, new Class[]{Map.class}, new String[]{"paramName"}));
        when(joinPointB.getSignature()).thenReturn(signatureB);

        when(joinPointB.getArgs()).thenReturn(new Object[]{Map.of("first", "firstValue", "second", "secondValue")});
        when(joinPointB.getTarget()).thenReturn(this);

        throttleB = new MockedThrottle("{#paramName.get('second'), #paramName.get('first')}", "'my.testing.group.B'");
    }

    void mockC() throws Throwable {
        joinPointC = mock(ProceedingJoinPoint.class);
        when(joinPointC.proceed()).thenReturn(null);
        signatureC = spy(new MockedMethodSignature(Void.TYPE, new Class[]{Object.class, Object.class}, new String[]{
                "paramA", "paramB"}));
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
            taskSchedulerTasks.put(invocation.getArgument(0, Runnable.class), invocation.getArgument(1, Instant.class));
            System.out.println("Scheduled task at " + invocation.getArgument(1, Instant.class));
            return new MockedFutureTask<>(Executors.callable(invocation.getArgument(0, Runnable.class)));
        });

        throttledFutures = new OrderedMap<>();
        lastRun = new OrderedMap<>();
        scheduledFutures = new TreeMap<>();

        Clock mockedClock = mock(Clock.class);
        when(mockedClock.instant()).then(invocation -> getInstant());

        transactionExecutor = mock(TransactionExecutor.class);
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(transactionExecutor).execute(any(Runnable.class));

        sut = new ThrottleAspect(throttledFutures, lastRun, scheduledFutures, taskScheduler, mockedClock, transactionExecutor);
    }

    Instant getInstant() {
        return clock.instant().truncatedTo(ChronoUnit.SECONDS);
    }

    void addSecond() {
        clock = Clock.offset(clock, Duration.ofSeconds(1));
    }

    @Test
    void threeImmediateCallsScheduleFirstCallWithLastTask() throws Throwable {
        final String[] params = new String[]{"param1", "param2", "param3", "param4", "param5", "param6"};
        // define a future as the return type of the method
        doReturn(Future.class).when(signatureA).getReturnType();

        final Supplier<String> methodTask = () -> "method result";
        final Supplier<String> anotherMethodResult = () -> "another method result";

        final ThrottledFuture<String> methodFuture = new ThrottledFuture<>();
        methodFuture.update(methodTask);

        // for each method call, make new future with "another method task"
        doAnswer(invocation -> new ThrottledFuture<String>().update(anotherMethodResult)).when(joinPointA).proceed();

        final Instant firstCall = getInstant();
        // simulate first call
        when(joinPointA.getArgs()).thenReturn(new Object[]{params[0], params[1]});
        sut.throttleMethodCall(joinPointA, throttleA);

        addSecond();
        // simulate second call
        when(joinPointA.getArgs()).thenReturn(new Object[]{params[2], params[3]});
        sut.throttleMethodCall(joinPointA, throttleA);
        addSecond();

        // change the return value of the method to the prepared future
        doReturn(methodFuture).when(joinPointA).proceed();

        // simulate last call
        when(joinPointA.getArgs()).thenReturn(new Object[]{params[4], params[5]});
        sut.throttleMethodCall(joinPointA, throttleA);

        // there should be only a single scheduled future
        assertEquals(1, scheduledFutures.size());
        assertEquals(1, taskSchedulerTasks.size());

        final Instant scheduledAt = taskSchedulerTasks.getValue(0);
        final Runnable scheduledTask = taskSchedulerTasks.getKey(0);
        assertNotNull(scheduledAt);
        assertNotNull(scheduledTask);
        // the task should be scheduled at the first call
        assertEquals(firstCall, scheduledAt);

        final ThrottledFuture<Object> future = throttledFutures.getValue(0);
        assertNotNull(future);

        // fulfill the future
        scheduledTask.run();
        // the future should be completed
        assertTrue(future.isDone());
        // check that the task in the future is from the last method call
        assertEquals(methodTask.get(), future.get());
    }

    @Test
    void callsInThrottleIntervalAreMerged() throws Throwable {
        final String[] params = new String[]{"param1", "param2", "param3", "param4", "param5", "param6"};
        // define a future as the return type of the method
        doReturn(Future.class).when(signatureA).getReturnType();

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

    @SuppressWarnings("unchecked")
    @Test
    void schedulesNewFutureWhenTheOldOneIsCompleted() throws Throwable {
        doReturn(Future.class).when(signatureA).getReturnType();
        when(joinPointA.proceed()).then(invocation -> ThrottledFuture.of(()->"result"));
        Future<Object> firstFuture = (Future<Object>) sut.throttleMethodCall(joinPointA, throttleA);
        addSecond();

        assertNotNull(firstFuture);
        assertFalse(firstFuture.isDone());
        assertFalse(firstFuture.isCancelled());

        assertEquals(1, taskSchedulerTasks.size());
        taskSchedulerTasks.forEach((runnable, instant) -> runnable.run());
        taskSchedulerTasks.clear();
        assertTrue(firstFuture.isDone());
        assertFalse(firstFuture.isCancelled());

        Future<Object> secondFuture = (Future<Object>) sut.throttleMethodCall(joinPointA, throttleA);
        addSecond();

        assertNotNull(secondFuture);
        assertFalse(secondFuture.isDone());
        assertFalse(secondFuture.isCancelled());

        assertEquals(1, taskSchedulerTasks.size());
        taskSchedulerTasks.forEach((runnable, instant) -> runnable.run());
        taskSchedulerTasks.clear();
        assertTrue(secondFuture.isDone());
        assertFalse(secondFuture.isCancelled());

        assertNotEquals(firstFuture, secondFuture);

        assertEquals(1, scheduledFutures.size());
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

        assertThrows(RuntimeException.class, () -> taskSchedulerTasks.forEach((r, i) -> r.run()));
    }

    @Test
    void exceptionPropagatedFutureTask() throws Throwable {
        when(joinPointA.proceed()).then(invocation -> new ThrottledFuture<>().update(() -> {
            throw new RuntimeException();
        }));
        signatureA.setReturnType(Future.class);

        sut.throttleMethodCall(joinPointA, throttleA);

        assertThrows(RuntimeException.class, () -> taskSchedulerTasks.forEach((r, i) -> r.run()));
    }
}
