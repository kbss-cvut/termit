package cz.cvut.kbss.termit.util.throttle;

import com.vladsch.flexmark.util.collection.OrderedMap;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.AbstractMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import static cz.cvut.kbss.termit.util.throttle.ThrottleAspect.THROTTLE_THRESHOLD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class ThrottleAspectTest {

    OrderedMap<String, ThrottledFuture<Object>> throttledFutures;

    OrderedMap<String, Instant> lastRun;

    NavigableMap<String, Future<Object>> scheduledFutures;

    TaskScheduler taskScheduler;

    OrderedMap<Runnable, Instant> taskSchedulerTasks;

    ThrottleAspect sut;

    Throttle throttleA;

    Throttle throttleB;

    MockedMethodSignature signatureA;

    MockedMethodSignature signatureB;

    ProceedingJoinPoint joinPointA;

    ProceedingJoinPoint joinPointB;

    Clock clock = Clock.fixed(Instant.now(), ZoneId.of("UTC"));

    void mockA() throws Throwable {
        joinPointA = mock(ProceedingJoinPoint.class);
        when(joinPointA.proceed()).thenReturn(null);
        signatureA = spy(new MockedMethodSignature(Void.TYPE, new Class[]{Object.class, Object.class}, new String[]{
                "paramA", "paramB"}));
        when(joinPointA.getSignature()).thenReturn(signatureA);
        when(joinPointA.getArgs()).thenReturn(new Object[]{new Object(), new Object()});
        when(joinPointA.getTarget()).thenReturn(this);

        throttleA = new MockedThrottle("'string literal'", "my.testing", "");
    }

    void mockB() throws Throwable {
        joinPointB = mock(ProceedingJoinPoint.class);
        when(joinPointB.proceed()).thenReturn(null);
        signatureB = spy(new MockedMethodSignature(Void.class, new Class[]{Map.class}, new String[]{"paramName"}));
        when(joinPointB.getSignature()).thenReturn(signatureB);

        when(joinPointB.getArgs()).thenReturn(new Object[]{Map.of("first", "firstValue", "second", "secondValue")});
        when(joinPointB.getTarget()).thenReturn(this);


        throttleB = new MockedThrottle("{param.get('second'), param.get('first')}", "my.testing.group", "");
    }

    @BeforeEach
    void beforeEach() throws Throwable {
        mockA();
        mockB();

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

        sut = new ThrottleAspect(throttledFutures, lastRun, scheduledFutures, taskScheduler, mockedClock);
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
        // simulate first call with params 0 & 1
        when(joinPointA.getArgs()).thenReturn(new Object[]{params[0], params[1]});
        final Object result1 = sut.throttleMethodCall(joinPointA, throttleA);

        addSecond();
        // simulate second call
        when(joinPointA.getArgs()).thenReturn(new Object[]{params[2], params[3]});
        final Object result2 = sut.throttleMethodCall(joinPointA, throttleA);
        addSecond();

        // change the return value of the method to the prepared future
        doReturn(methodFuture).when(joinPointA).proceed();

        // simulate last call
        when(joinPointA.getArgs()).thenReturn(new Object[]{params[4], params[5]});
        final Object result3 = sut.throttleMethodCall(joinPointA, throttleA);

        // all three calls returned the same future
        // this ensures that calls are actually batched and single result
        // satisfies all batched calls
        assertEquals(result1, result2);
        assertEquals(result1, result3);

        // there should be only a single scheduled future
        assertEquals(1, scheduledFutures.size());
        assertEquals(1, taskSchedulerTasks.size());

        final Instant scheduledAt = taskSchedulerTasks.getValue(0);
        final Runnable scheduledTask = taskSchedulerTasks.getKey(0);
        assertNotNull(scheduledAt);
        assertNotNull(scheduledTask);
        // the task should be scheduled at the first call
        assertEquals(firstCall.plus(THROTTLE_THRESHOLD), scheduledAt);

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
    void schedulesNewFutureWhenTheOldOneIsCompleted() throws Throwable {
        sut.throttleMethodCall(joinPointA, throttleA);
        addSecond();

        final AbstractMap.SimpleImmutableEntry<String, Future<Object>> firstEntry = new AbstractMap.SimpleImmutableEntry<>(scheduledFutures.firstEntry());
        final Runnable firstTask = taskSchedulerTasks.getKey(0);
        assertNotNull(firstTask);
        firstTask.run();

        sut.throttleMethodCall(joinPointA, throttleA);
        addSecond();

        assertEquals(1, scheduledFutures.size());
        assertEquals(2, taskSchedulerTasks.size());
        final Future<Object> secondFuture = scheduledFutures.get(firstEntry.getKey());
        assertNotEquals(firstEntry.getValue(), secondFuture);
        assertTrue(firstEntry.getValue().isDone());
        assertFalse(secondFuture.isDone());
        assertFalse(secondFuture.isCancelled());
    }

}
