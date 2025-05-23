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

import cz.cvut.kbss.termit.TermItApplication;
import cz.cvut.kbss.termit.event.ClearLongRunningTaskQueueEvent;
import cz.cvut.kbss.termit.exception.TermItException;
import cz.cvut.kbss.termit.exception.ThrottleAspectException;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Pair;
import cz.cvut.kbss.termit.util.longrunning.LongRunningTaskScheduler;
import cz.cvut.kbss.termit.util.longrunning.LongRunningTasksRegistry;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.DataBindingPropertyAccessor;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.expression.spel.support.StandardTypeLocator;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_SINGLETON;

/**
 * @implNote The aspect is configured in {@code spring-aop.xml}, this uses Spring AOP instead of AspectJ.
 * @see Throttle
 */
@Order
@Scope(SCOPE_SINGLETON)
@Component("throttleAspect")
@Profile("!test")
public class ThrottleAspect extends LongRunningTaskScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(ThrottleAspect.class);

    /**
     * <p>Throttled futures are returned as results of method calls.</p>
     * <p>Tasks inside them can be replaced by a newer ones allowing
     * to merge multiple (throttled) method calls into a single one while always executing the newest one possible.</p>
     * <p>A task inside a throttled future represents
     * a heavy/long-running task acquired from the body of an throttled method</p>
     *
     * @implSpec Synchronize in the field declaration order before modification
     */
    private final Map<Identifier, ThrottledFuture<Object>> throttledFutures;

    /**
     * The last run is updated every time a task is finished.
     *
     * @implSpec Synchronize in the field declaration order before modification
     */
    private final Map<Identifier, Instant> lastRun;

    /**
     * Scheduled futures are returned from {@link #taskScheduler}. Futures are completed by execution of tasks created
     * in {@link #createRunnableToSchedule}. Records about them are used for their cancellation in case of debouncing.
     *
     * @implSpec Synchronize in the field declaration order before modification
     */
    private final NavigableMap<Identifier, Future<Object>> scheduledFutures;

    /**
     * Thread safe set holding identifiers of threads that are currently executing a throttled task.
     */
    private final Set<Long> throttledThreads = ConcurrentHashMap.newKeySet();

    /**
     * Parser for Spring Expression Language
     */
    private final ExpressionParser parser = new SpelExpressionParser();

    private final TaskScheduler taskScheduler;

    /**
     * A base context for evaluation of SpEL expressions
     */
    private final StandardEvaluationContext standardEvaluationContext;

    /**
     * Used for acquiring {@link #lastRun} timestamps.
     *
     * @implNote for testing purposes
     */
    private final Clock clock;

    /**
     * Wrapper for executions in a transaction context
     */
    private final SynchronousTransactionExecutor transactionExecutor;

    /**
     * A timestamp of the last time maps were cleaned. The reference might be null.
     *
     * @see #clearOldFutures()
     */
    private final AtomicReference<Instant> lastClear;

    private final Configuration configuration;

    @Autowired
    public ThrottleAspect(@Qualifier("longRunningTaskScheduler") TaskScheduler taskScheduler,
                          SynchronousTransactionExecutor transactionExecutor,
                          LongRunningTasksRegistry longRunningTasksRegistry, Configuration configuration) {
        super(longRunningTasksRegistry);
        this.taskScheduler = taskScheduler;
        this.transactionExecutor = transactionExecutor;
        this.configuration = configuration;
        throttledFutures = new HashMap<>();
        lastRun = new HashMap<>();
        scheduledFutures = new TreeMap<>();
        clock = Clock.systemUTC(); // used by Instant.now() by default
        standardEvaluationContext = makeDefaultContext();
        lastClear = new AtomicReference<>(Instant.now(clock));
    }

    /**
     * Constructor for testing environment
     */
    protected ThrottleAspect(Map<Identifier, ThrottledFuture<Object>> throttledFutures,
                             Map<Identifier, Instant> lastRun,
                             NavigableMap<Identifier, Future<Object>> scheduledFutures, TaskScheduler taskScheduler,
                             Clock clock, SynchronousTransactionExecutor transactionExecutor,
                             LongRunningTasksRegistry longRunningTasksRegistry, Configuration configuration) {
        super(longRunningTasksRegistry);
        this.throttledFutures = throttledFutures;
        this.lastRun = lastRun;
        this.scheduledFutures = scheduledFutures;
        this.taskScheduler = taskScheduler;
        this.clock = clock;
        this.transactionExecutor = transactionExecutor;
        this.configuration = configuration;
        standardEvaluationContext = makeDefaultContext();
        lastClear = new AtomicReference<>(Instant.now(clock));
    }

    /**
     * Creates a default evaluation context for SpEL processing.
     *
     * @return {@link StandardEvaluationContext} with {@link DataBindingPropertyAccessor}
     */
    private static StandardEvaluationContext makeDefaultContext() {
        StandardEvaluationContext standardEvaluationContext = new StandardEvaluationContext();
        standardEvaluationContext.addPropertyAccessor(DataBindingPropertyAccessor.forReadOnlyAccess());

        final ClassLoader loader = ThrottleAspect.class.getClassLoader();
        final StandardTypeLocator typeLocator = new StandardTypeLocator(loader);

        final String basePackage = TermItApplication.class.getPackageName();
        Arrays.stream(loader.getDefinedPackages()).map(Package::getName).filter(s -> s.indexOf(basePackage) == 0)
              .forEach(typeLocator::registerImport);

        standardEvaluationContext.setTypeLocator(typeLocator);
        return standardEvaluationContext;
    }

    /**
     * Prevents accepting new tasks by synchronization
     * and cancels all scheduled tasks.
     */
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @EventListener(ClearLongRunningTaskQueueEvent.class)
    public void onClearLongRunningTaskQueueEvent() {
        synchronized (throttledFutures) { // synchronize in the filed declaration order
            synchronized (lastRun) {
                synchronized (scheduledFutures) {
                    LOG.info("Clearing throttled tasks...");

                    long count = 0;
                    Iterator<Map.Entry<Identifier, ThrottledFuture<Object>>> throttledIt =
                            throttledFutures.entrySet().iterator();

                    while(throttledIt.hasNext()) {
                        final Map.Entry<Identifier, ThrottledFuture<Object>> entry = throttledIt.next();
                        final ThrottledFuture<Object> future = entry.getValue();
                        final Identifier identifier = entry.getKey();
                        if(future.isRunning() || future.isDone()) continue;

                        // cancel the throttled future
                        future.cancel(false);
                        // cancel the scheduled future
                        Optional.ofNullable(scheduledFutures.get(identifier))
                                .ifPresent(scheduled -> {
                                    scheduled.cancel(false);
                                    if (scheduled.isCancelled()) {
                                        scheduledFutures.remove(identifier);
                                    }
                                });
                        if (future.isCancelled()) {
                            throttledIt.remove();
                        }
                        count++;
                        notifyTaskChanged(future); // task canceled - clearing queue
                    }
                    clearOldFutures();
                    LOG.info("Cancelled {} pending throttled tasks", count);
                }
            }
        }
    }

    /**
     * @return future or null
     * @throws TermItException        when the target method throws
     * @throws IllegalCallerException when the annotated method returns another type than {@code void}, {@link Void} or
     *                                {@link Future}
     * @implNote Around advice configured in {@code spring-aop.xml}
     */
    public @Nullable Object throttleMethodCall(@Nonnull ProceedingJoinPoint joinPoint,
                                               @Nonnull Throttle throttleAnnotation) throws Throwable {

        // if the current thread is already executing a throttled code, we want to skip further throttling
        if (throttledThreads.contains(Thread.currentThread().getId())) {
            // proceed with method execution
            final Object result = joinPoint.proceed();
            if (result instanceof ThrottledFuture<?> throttledFuture) {
                LOG.trace("Running throttled future synchronously (current thread is already throttled)");
                // directly run throttled future
                throttledFuture.run(null);
                return throttledFuture;
            }
            return result;
        }

        return doThrottle(joinPoint, throttleAnnotation);
    }

    private synchronized @Nullable Object doThrottle(@Nonnull ProceedingJoinPoint joinPoint,
                                                     @Nonnull Throttle throttleAnnotation) throws Throwable {

        final MethodSignature signature = (MethodSignature) joinPoint.getSignature();

        // construct the throttle instance key
        final Identifier identifier = makeIdentifier(joinPoint, throttleAnnotation);
        LOG.trace("Throttling task with key '{}'", identifier);

        synchronized (scheduledFutures) {
            if (!identifier.getGroup().isBlank()) {
                // check if there is a task with lower group
                // and if so, cancel this task in favor of the lower group
                final Map.Entry<Identifier, Future<Object>> lowerEntry = scheduledFutures.lowerEntry(identifier);
                if (lowerEntry != null) {
                    final Future<Object> lowerFuture = lowerEntry.getValue();
                    boolean hasGroupPrefix = identifier.hasGroupPrefix(lowerEntry.getKey().getGroup());
                    if (hasGroupPrefix && !lowerFuture.isDone()) {
                        LOG.trace("Throttling canceled due to scheduled lower task '{}'", lowerEntry.getKey());
                        return ThrottledFuture.canceled();
                    }
                }

                cancelWithHigherGroup(identifier);
            }
        }

        // if there is a scheduled task and this throttled instance was executed in the last configuration.getThrottleThreshold()
        // cancel the scheduled task
        // -> the execution is further delayed
        Future<Object> oldScheduledFuture = scheduledFutures.get(identifier);
        boolean throttleExpired = isThresholdExpired(identifier);
        if (oldScheduledFuture != null && !throttleExpired) {
            oldScheduledFuture.cancel(false);
            synchronized (scheduledFutures) {
                scheduledFutures.remove(identifier);
            }
        }

        // acquire a throttled future from a map, or make a new one
        ThrottledFuture<Object> oldThrottledFuture = throttledFutures.getOrDefault(identifier, new ThrottledFuture<>());

        final Pair<Runnable, ThrottledFuture<Object>> pair = getFutureTask(joinPoint, identifier, oldThrottledFuture);
        ThrottledFuture<Object> future = pair.getSecond();
        future.setName(throttleAnnotation.name());
        // update the throttled future in the map, it might be just the same future, but it might be a new one
        synchronized (throttledFutures) {
            throttledFutures.put(identifier, future);
        }

        Object result = resultVoidOrFuture(signature, future);

        if (future.isDone() || future.isRunning()) {
            return result;
        }

        if (oldScheduledFuture == null || oldThrottledFuture != future || oldScheduledFuture.isDone()) {
            boolean oldFutureIsDone = oldScheduledFuture == null || oldScheduledFuture.isDone();
            if (oldThrottledFuture != future) {
                oldThrottledFuture.then(ignored -> schedule(identifier, pair.getFirst(),
                                                            throttleExpired && oldFutureIsDone)
                );
                notifyTaskChanged(oldThrottledFuture); // the old future maybe changed its state
            } else {
                schedule(identifier, pair.getFirst(), throttleExpired && oldFutureIsDone);
            }
            notifyTaskChanged(future); // task pending
        }

        return result;
    }

    /**
     * Maps parameter names from the method signature to their values from {@link JoinPoint#getArgs()}
     *
     * @param map       to fill
     * @param signature the method signature
     * @param joinPoint the join point
     */
    private static void resolveParameters(Map<String, Object> map, MethodSignature signature, JoinPoint joinPoint) {
        final String[] paramNames = signature.getParameterNames();
        final Object[] params = joinPoint.getArgs();

        if (paramNames == null || params == null || params.length != paramNames.length) {
            return;
        }

        for (int i = 0; i < params.length; i++) {
            map.putIfAbsent(paramNames[i], params[i]);
        }
    }

    /**
     * Creates a new {@link EvaluationContext} for SpEL evaluation where {@code parameters} are available for referencing.
     *
     * @param joinPoint  method call {@link JoinPoint}
     * @param parameters method call parameters
     * @return a new {@link EvaluationContext} that delegates to {@link #standardEvaluationContext}
     * @see #makeDefaultContext()
     */
    private EvaluationContext makeContext(JoinPoint joinPoint, Map<String, Object> parameters) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        standardEvaluationContext.applyDelegatesTo(context);
        context.setRootObject(joinPoint.getTarget());
        context.setVariables(parameters);
        return context;
    }

    /**
     * Takes an existing throttled future and creates a runnable that can be scheduled for future execution.
     *
     * @param joinPoint  method call {@link JoinPoint}
     * @param identifier throttle {@link Identifier}
     * @param future     existing (old) throttled future
     * @return a pair of runnable to schedule and resulting throttled future (which may or may not be the same as the provided one)
     * @throws Throwable when {@code joinPoint.proceed()} throws,
     *                   or {@link ThrottleAspectException} when the method signature from the {@code joinPoint} is invalid.
     */
    private Pair<Runnable, ThrottledFuture<Object>> getFutureTask(@Nonnull ProceedingJoinPoint joinPoint,
                                                                  @Nonnull Identifier identifier,
                                                                  @Nonnull ThrottledFuture<Object> future)
            throws Throwable {

        final MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        final Class<?> returnType = methodSignature.getReturnType();
        final boolean isFuture = returnType.isAssignableFrom(ThrottledFuture.class);
        final boolean isVoid = returnType.equals(Void.class) || returnType.equals(Void.TYPE);

        ThrottledFuture<Object> throttledFuture = future;

        // Sets the task to the future.
        // If the annotated method returns throttled future, transfer the new task into the future
        //   replacing the old one.
        // If the method does not return a throttled future,
        // fill the future with a task which calls the annotated method returning the result

        // the future must contain the same type - ensured by accessing with the unique key
        if (isFuture) {
            Object result = joinPoint.proceed();
            if (result instanceof ThrottledFuture<?> throttledMethodFuture) {
                // future acquired by key or a new future supplied, ensuring the same type
                // ThrottledFuture#updateOther will create a new future when required
                final ThrottledFuture<Object> methodFuture = (ThrottledFuture<Object>) throttledMethodFuture;
                if (throttledMethodFuture.isDone()) {
                    methodFuture.consumeCallbacks(throttledFuture);
                    throttledFuture = methodFuture;
                } else {
                    // transfer the newer task from methodFuture -> to the (old) throttled future
                    throttledFuture = methodFuture.transfer(throttledFuture);
                }
            } else {
                throw new ThrottleAspectException("Returned value is not a ThrottledFuture");
            }
        } else if (isVoid) {
            throttledFuture = throttledFuture.update(() -> {
                try {
                    return joinPoint.proceed();
                } catch (Throwable e) {
                    // exception happened inside throttled method,
                    // and the method returns null, if we rethrow the exception
                    // it will be stored inside the future
                    // and never retrieved (as the method returned null)
                    LOG.error("Exception thrown during task execution", e);
                    throw new TermItException(e);
                }
            }, List.of());
        } else {
            throw new ThrottleAspectException(
                    "Invalid return type for " + joinPoint.getSignature() + " annotated with @Throttle, only Future or void allowed!");
        }

        final boolean withTransaction = methodSignature.getMethod() != null && methodSignature.getMethod()
                                                                                              .isAnnotationPresent(
                                                                                                      Transactional.class);

        // create a task which will be scheduled with executor
        final Runnable toSchedule = createRunnableToSchedule(throttledFuture, identifier, withTransaction);

        return new Pair<>(toSchedule, throttledFuture);
    }

    /**
     * @return the number of throttled futures that are neither done nor running.
     */
    private long countRemaining() {
        synchronized (throttledFutures) {
            return throttledFutures.values().stream().filter(f -> !f.isDone() && !f.isRunning()).count();
        }
    }

    /**
     * @return count of throttled threads
     */
    private long countRunning() {
        return throttledThreads.size();
    }

    /**
     * Creates runnable that can be scheduled for throttled future execution.
     * The {@link Runnable} will handle thread throttling, restoring security context
     * and {@link cz.cvut.kbss.termit.util.longrunning.LongRunningTask} state change notification
     *
     * @param throttledFuture the {@link ThrottledFuture} to execute in the returned runnable
     * @param identifier      throttle {@link Identifier}
     * @param withTransaction whether the future should be executed in {@link Transactional} context
     * @return {@link Runnable} that executes the future
     */
    private Runnable createRunnableToSchedule(ThrottledFuture<?> throttledFuture, Identifier identifier,
                                              boolean withTransaction) {
        final Supplier<SecurityContext> securityContext = SecurityContextHolder.getDeferredContext();
        return () -> {
            if (throttledFuture.isDone()) {
                return;
            }
            // mark the thread as throttled
            final Long threadId = Thread.currentThread().getId();
            throttledThreads.add(threadId);

            LOG.trace("Running throttled task [{} left] [{} running] '{}'", countRemaining() - 1, countRunning(),
                      identifier);

            // restore the security context
            SecurityContextHolder.setContext(securityContext.get());
            try {
                // fulfill the future
                if (withTransaction) {
                    transactionExecutor.execute(() -> throttledFuture.run(this::notifyTaskChanged)); // task running
                } else {
                    throttledFuture.run(this::notifyTaskChanged); // task running
                }
                // update last run timestamp
                synchronized (lastRun) {
                    lastRun.put(identifier, Instant.now(clock));
                }
            } finally {
                if (!throttledFuture.isDone()) {
                    throttledFuture.cancel(false);
                }
                notifyTaskChanged(throttledFuture); // task done
                // clear the security context
                SecurityContextHolder.clearContext();
                LOG.trace("Finished throttled task [{} left] [{} running] '{}'", countRemaining(), countRunning() - 1,
                          identifier);

                clearOldFutures();

                // remove throttled mark
                throttledThreads.remove(threadId);
            }
        };
    }

    /**
     * Discards futures from {@link #throttledFutures}, {@link #lastRun} and {@link #scheduledFutures} maps.
     * <p>
     * Every completed future for which a {@link Configuration#getThrottleDiscardThreshold()} expired is discarded.
     *
     * @see #isThresholdExpired(Identifier)
     */
    private void clearOldFutures() {
        // if the last clear was performed less than a threshold ago, skip it for now
        Instant last = lastClear.get();
        if (last.isAfter(Instant.now(clock).minus(configuration.getThrottleThreshold())
                                .minus(configuration.getThrottleDiscardThreshold()))) {
            return;
        }
        if (!lastClear.compareAndSet(last, Instant.now(clock))) {
            return;
        }
        synchronized (throttledFutures) { // synchronize in the filed declaration order
            synchronized (lastRun) {
                synchronized (scheduledFutures) {
                    Stream.of(throttledFutures.keySet().stream(), scheduledFutures.keySet().stream(), lastRun.keySet()
                                                                                                             .stream())
                          .flatMap(s -> s).distinct().toList() // ensures safe modification of maps
                          .forEach(identifier -> {
                              if (isThresholdExpiredByMoreThan(identifier,
                                                               configuration.getThrottleDiscardThreshold())) {
                                  Optional.ofNullable(throttledFutures.get(identifier)).ifPresent(throttled -> {
                                      if (throttled.isDone()) {
                                          throttledFutures.remove(identifier);
                                      }
                                  });
                                  Optional.ofNullable(scheduledFutures.get(identifier)).ifPresent(scheduled -> {
                                      if (scheduled.isDone()) {
                                          scheduledFutures.remove(identifier);
                                      }
                                  });
                                  lastRun.remove(identifier);
                              }
                          });
                }
            }
        }
    }

    /**
     * @param identifier of the task
     * @param duration   to add to the throttle threshold
     * @return Whether the last time when a task with specified {@code identifier} run is older than
     * ({@link Configuration#getThrottleThreshold()} + {@code duration})
     */
    private boolean isThresholdExpiredByMoreThan(Identifier identifier, Duration duration) {
        return lastRun.getOrDefault(identifier, Instant.MAX)
                      .isBefore(Instant.now(clock).minus(configuration.getThrottleThreshold()).minus(duration));
    }

    /**
     * @return Whether the time when the identifier last run is older than the threshold, true when the task had never
     * run
     */
    private boolean isThresholdExpired(Identifier identifier) {
        return lastRun.getOrDefault(identifier, Instant.EPOCH)
                      .isBefore(Instant.now(clock).minus(configuration.getThrottleThreshold()));
    }

    /**
     * Schedules the {@code task} for {@link ThrottledFuture} execution
     *
     * @param identifier  throttle {@link Identifier}
     * @param task        the {@link Runnable} to schedule
     * @param immediately whether the {@link Runnable} should be scheduled as soon as possible
     */
    @SuppressWarnings("unchecked")
    private void schedule(Identifier identifier, Runnable task, boolean immediately) {
        Instant startTime = Instant.now(clock).plus(configuration.getThrottleThreshold());
        if (immediately) {
            startTime = Instant.now(clock);
        }
        synchronized (scheduledFutures) {
            Future<?> scheduled = taskScheduler.schedule(task, startTime);
            // casting the type parameter to Object
            scheduledFutures.put(identifier, (Future<Object>) scheduled);
        }
    }

    private void cancelWithHigherGroup(Identifier throttleAnnotation) {
        if (throttleAnnotation.getGroup().isBlank()) {
            return;
        }
        synchronized (throttledFutures) { // synchronize in the filed declaration order
            synchronized (scheduledFutures) {
                // look for any futures with higher group
                // cancel them and remove from maps
                Future<Object> higherFuture;
                Identifier higherKey = scheduledFutures.higherKey(new Identifier(throttleAnnotation.getGroup(), ""));
                while (higherKey != null) {
                    if (!higherKey.hasGroupPrefix(throttleAnnotation.getGroup()) || higherKey.getGroup()
                                                                                             .equals(throttleAnnotation.getGroup())) {
                        break;
                    }

                    higherFuture = scheduledFutures.get(higherKey);
                    higherFuture.cancel(false);
                    final ThrottledFuture<Object> throttledFuture = throttledFutures.get(higherKey);

                    // cancels future if it's not null (should not be) and removes it from map if it was canceled
                    if (throttledFuture != null && throttledFuture.cancel(false)) {
                        throttledFutures.remove(higherKey);
                        notifyTaskChanged(throttledFuture); // task canceled (higher group)
                    }

                    scheduledFutures.remove(higherKey);

                    higherKey = scheduledFutures.higherKey(higherKey);
                }
            }
        }
    }

    private Identifier makeIdentifier(JoinPoint joinPoint, Throttle throttleAnnotation) throws IllegalCallerException {
        final String identifier = constructIdentifier(joinPoint, throttleAnnotation.value());
        final String groupIdentifier = constructIdentifier(joinPoint, throttleAnnotation.group());

        return new Identifier(groupIdentifier, joinPoint.getSignature().toShortString() + "-" + identifier);
    }

    /**
     * Resolves the return type from the {@link MethodSignature}
     *
     * @param signature throttled method signature
     * @param future    throttled future
     * @return the throttled future when method signature returns a type assignable from the {@link ThrottledFuture},
     * when the method returns void, null is returned, exception is thrown otherwise.
     * @throws IllegalCallerException when the method by the signature returns anything else than a future or void.
     */
    private @Nullable Object resultVoidOrFuture(@Nonnull MethodSignature signature, ThrottledFuture<Object> future)
            throws IllegalCallerException {
        Class<?> returnType = signature.getReturnType();
        if (returnType.isAssignableFrom(ThrottledFuture.class)) {
            return future;
        }
        if (Void.TYPE.equals(returnType) || Void.class.equals(returnType)) {
            return null;
        }
        throw new ThrottleAspectException(
                "Invalid return type for " + signature + " annotated with @Debounce, only Future or void allowed!");
    }

    /**
     * Creates throttle identifier by evaluating a SpEL in {@code expression}.
     *
     * @param joinPoint  method call {@link JoinPoint}
     * @param expression SpEL to evaluate
     * @return resulting identifier
     * @throws ThrottleAspectException When the SpEL {@code expression} is not resolved into a {@link Collection} or {@link String}.
     * @see #makeContext(JoinPoint, Map)
     */
    @SuppressWarnings({"unchecked"})
    private @Nonnull String constructIdentifier(JoinPoint joinPoint, String expression) throws ThrottleAspectException {
        if (expression == null || expression.isBlank()) {
            return "";
        }

        final Map<String, Object> parameters = new HashMap<>();
        resolveParameters(parameters, (MethodSignature) joinPoint.getSignature(), joinPoint);

        final EvaluationContext context = makeContext(joinPoint, parameters);

        final Expression identifierExp = parser.parseExpression(expression);
        try {
            Object result = identifierExp.getValue(context);

            if (result instanceof String stringResult) {
                return stringResult;
            }

            // casting the expression result to the list of objects
            // exception handled and rethrown by try-catch
            Collection<Object> identifierList = (Collection<Object>) identifierExp.getValue(context);
            Objects.requireNonNull(identifierList);
            return identifierList.stream().map(Object::toString).collect(Collectors.joining("-"));
        } catch (EvaluationException | ClassCastException | NullPointerException e) {
            throw new ThrottleAspectException(
                    "The expression: '" + expression + "' has not been resolved to a Collection<Object> or String", e);
        }
    }

    /**
     * A composed identifier of a throttled instance.
     * <pre><code>
     *     String group
     *     String identifier
     * </code></pre>
     * Implements comparable, first comparing group, then identifier.
     */
    protected static class Identifier extends Pair.ComparablePair<String, String> {

        public Identifier(String group, String identifier) {
            super(group, identifier);
        }

        public String getGroup() {
            return this.getFirst();
        }

        public String getIdentifier() {
            return this.getSecond();
        }

        public boolean hasGroupPrefix(@Nonnull String group) {
            return this.getGroup().indexOf(group) == 0 && !this.getGroup().isBlank() && !group.isBlank();
        }

        @Override
        public String toString() {
            return "ThrottleAspect.Identifier{group='" + getGroup() + "',identifier='" + getIdentifier() + "'}";
        }
    }
}
