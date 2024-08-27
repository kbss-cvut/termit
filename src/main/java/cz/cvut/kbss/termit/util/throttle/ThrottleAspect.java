package cz.cvut.kbss.termit.util.throttle;

import cz.cvut.kbss.termit.TermItApplication;
import cz.cvut.kbss.termit.exception.TermItException;
import cz.cvut.kbss.termit.exception.ThrottleAspectException;
import cz.cvut.kbss.termit.util.Pair;
import cz.cvut.kbss.termit.util.longrunning.LongRunningTask;
import cz.cvut.kbss.termit.util.longrunning.LongRunningTaskRegister;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
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
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static cz.cvut.kbss.termit.util.Constants.THROTTLE_THRESHOLD;
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_SINGLETON;

/**
 * @implNote The aspect is configured in {@code spring-aop.xml}, this uses Spring AOP instead of AspectJ.
 */
@Order
@Scope(SCOPE_SINGLETON)
@Component("throttleAspect")
@Profile("!test")
public class ThrottleAspect implements LongRunningTaskRegister {

    private static final Logger LOG = LoggerFactory.getLogger(ThrottleAspect.class);

    /**
     * group, identifier -> future
     *
     * @implSpec Synchronize in the field definition order before modification
     */
    private final Map<Identifier, ThrottledFuture<Object>> throttledFutures;

    /**
     * @implSpec Synchronize in the field definition order before modification
     */
    private final Map<Identifier, Instant> lastRun;

    /**
     * group, identifier -> future
     *
     * @implSpec Synchronize in the field definition order before modification
     */
    private final NavigableMap<Identifier, Future<Object>> scheduledFutures;

    /**
     * thread safe set holding identifiers of threads
     * currently executing a throttled task
     */
    private final Set<Long> throttledThreads = ConcurrentHashMap.newKeySet();

    private final ExpressionParser parser = new SpelExpressionParser();

    private final TaskScheduler taskScheduler;

    private final StandardEvaluationContext standardEvaluationContext;

    private final Clock clock;

    private final Executor transactionExecutor;

    private final @NotNull AtomicReference<Instant> lastClear;

    @Autowired
    public ThrottleAspect(@Qualifier("threadPoolTaskScheduler") TaskScheduler taskScheduler, TransactionExecutor transactionExecutor) {
        this.taskScheduler = taskScheduler;
        this.transactionExecutor = transactionExecutor;
        throttledFutures = new HashMap<>();
        lastRun = new HashMap<>();
        scheduledFutures = new TreeMap<>();
        clock = Clock.systemUTC(); // used by Instant.now() by default
        standardEvaluationContext = makeDefaultContext();
        lastClear = new AtomicReference<>(Instant.now(clock));
    }

    protected ThrottleAspect(Map<Identifier, ThrottledFuture<Object>> throttledFutures,
                             Map<Identifier, Instant> lastRun,
                             NavigableMap<Identifier, Future<Object>> scheduledFutures, TaskScheduler taskScheduler,
                             Clock clock, TransactionExecutor transactionExecutor) {
        this.throttledFutures = throttledFutures;
        this.lastRun = lastRun;
        this.scheduledFutures = scheduledFutures;
        this.taskScheduler = taskScheduler;
        this.clock = clock;
        this.transactionExecutor = transactionExecutor;
        standardEvaluationContext = makeDefaultContext();
        lastClear = new AtomicReference<>(Instant.now(clock));
    }

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
     * @return future or null
     * @throws TermItException        when the target method throws
     * @throws IllegalCallerException when the annotated method returns another type than {@code void}, {@link Void} or {@link Future}
     * @implNote Around advice configured in {@code spring-aop.xml}
     */
    public synchronized @Nullable Object throttleMethodCall(@NotNull ProceedingJoinPoint joinPoint,
                                                            @NotNull Throttle throttleAnnotation) throws Throwable {

        // if the current thread is already executing a throttled code, we want to skip further throttling
        if (throttledThreads.contains(Thread.currentThread().getId())) {
            // proceed with method execution
            final Object result = joinPoint.proceed();
            if (result instanceof ThrottledFuture<?> throttledFuture) {
                // directly run throttled future
                throttledFuture.run();
                return throttledFuture;
            }
            return result;
        }

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
                    if (hasGroupPrefix && !lowerFuture.isDone() && !lowerFuture.isCancelled()) {
                        LOG.trace("Throttling canceled due to scheduled lower task '{}'", lowerEntry.getKey());
                        return ThrottledFuture.canceled();
                    }
                }

                cancelWithHigherGroup(identifier);
            }
        }

        // if there is a scheduled task and this throttled instance was executed in the last THROTTLE_THRESHOLD
        // cancel the scheduled task
        // -> the execution is further delayed
        Future<Object> oldFuture = scheduledFutures.get(identifier);
        boolean throttleExpired = isThresholdExpired(identifier);
        if (oldFuture != null && !throttleExpired) {
            oldFuture.cancel(false);
        }

        // acquire a throttled future from a map, or make a new one
        ThrottledFuture<Object> oldThrottledFuture = throttledFutures.getOrDefault(identifier, new ThrottledFuture<>());

        Pair<Runnable, ThrottledFuture<Object>> pair = getFutureTask(joinPoint, identifier, oldThrottledFuture);
        Runnable task = pair.getFirst();
        ThrottledFuture<Object> future = pair.getSecond();
        // update the throttled future in the map
        synchronized (throttledFutures) {
            throttledFutures.put(identifier, future);
        }

        Object result = resultVoidOrFuture(signature, future);

        if (future.isCompleted() || future.isRunning()) {
            return result;
        }

        if (oldFuture == null || oldFuture.isDone() || oldFuture.isCancelled()) {
            schedule(identifier, task, throttleExpired);
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

    private EvaluationContext makeContext(JoinPoint joinPoint, Map<String, Object> parameters) {
        StandardEvaluationContext context = new StandardEvaluationContext();
        standardEvaluationContext.applyDelegatesTo(context);
        context.setRootObject(joinPoint.getTarget());
        context.setVariables(parameters);
        return context;
    }

    private Pair<Runnable, ThrottledFuture<Object>> getFutureTask(@NotNull ProceedingJoinPoint joinPoint,
                                                                  @NotNull Identifier identifier,
                                                                  @NotNull ThrottledFuture<Object> future)
            throws Throwable {

        final MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        final Class<?> returnType = methodSignature.getReturnType();
        final boolean isFuture = returnType.isAssignableFrom(ThrottledFuture.class);

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
                if (throttledMethodFuture.isDone()) {
                    throttledFuture = (ThrottledFuture<Object>) throttledMethodFuture;
                } else {
                    throttledFuture = ((ThrottledFuture<Object>) throttledMethodFuture).transfer(throttledFuture);
                }
            } else {
                throw new ThrottleAspectException("Returned value is not a ThrottledFuture");
            }
        } else {
            throttledFuture = throttledFuture.update(() -> {
                try {
                    return joinPoint.proceed();
                } catch (Throwable e) {
                    // exception happened inside throttled method
                    throw new TermItException(e);
                }
            });
        }

        final boolean withTransaction = methodSignature.getMethod() != null && methodSignature.getMethod()
                                                                                              .isAnnotationPresent(Transactional.class);

        // create a task which will be scheduled with executor
        final Runnable toSchedule = createRunnableToSchedule(throttledFuture, identifier, withTransaction);

        return new Pair<>(toSchedule, throttledFuture);
    }

    private Runnable createRunnableToSchedule(ThrottledFuture<?> throttledFuture, Identifier identifier,
                                              boolean withTransaction) {
        final Supplier<SecurityContext> securityContext = SecurityContextHolder.getDeferredContext();
        return () -> {
            if (throttledFuture.isCancelled() || throttledFuture.isDone()) {
                return;
            }
            // mark the thread as throttled
            final Long threadId = Thread.currentThread().getId();
            throttledThreads.add(threadId);

            LOG.trace("Running throttled task [{} left] '{}'", scheduledFutures.size() - 1, identifier);

            // restore the security context
            SecurityContextHolder.setContext(securityContext.get());
            try {
                // update last run timestamp
                synchronized (lastRun) {
                    lastRun.put(identifier, Instant.now(clock));
                }
                // fulfill the future
                if (withTransaction) {
                    transactionExecutor.execute(throttledFuture::run);
                } else {
                    throttledFuture.run();
                }
            } finally {
                // clear the security context
                SecurityContextHolder.clearContext();
                LOG.trace("Throttled task run finished '{}'", identifier);

                clearOldFutures();

                // remove throttled mark
                throttledThreads.remove(threadId);
            }
        };
    }

    private void clearOldFutures() {
        // if the last clear was performed less than a threshold ago, skip it for now
        Instant last = lastClear.get();
        if (last.isAfter(Instant.now(clock).minus(THROTTLE_THRESHOLD))) {
            return;
        }
        if (!lastClear.compareAndSet(last, Instant.now(clock))) {
            return;
        }
        synchronized (throttledFutures) {
            synchronized (lastRun) {
                synchronized (scheduledFutures) {
                    Stream.of(throttledFutures.keySet().stream(), scheduledFutures.keySet().stream(), lastRun.keySet()
                                                                                                             .stream())
                          .flatMap(s -> s).distinct().toList() // ensures safe modification of maps
                          .forEach(identifier -> {
                              if (isThresholdExpired(identifier)) {
                                  Optional.ofNullable(throttledFutures.get(identifier)).ifPresent(throttled -> {
                                      if (throttled.isDone() || throttled.isCancelled()) {
                                          throttledFutures.remove(identifier);
                                      }
                                  });
                                  Optional.ofNullable(scheduledFutures.get(identifier)).ifPresent(scheduled -> {
                                      if (scheduled.isDone() || scheduled.isCancelled()) {
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
     * @return Whether the time when the identifier last run is older than the threshold,
     * true when the task had never run
     */
    private boolean isThresholdExpired(Identifier identifier) {
        return lastRun.getOrDefault(identifier, Instant.EPOCH).isBefore(Instant.now(clock).minus(THROTTLE_THRESHOLD));
    }

    @SuppressWarnings("unchecked")
    private void schedule(Identifier identifier, Runnable task, boolean immediately) {
        Instant startTime = Instant.now(clock).plus(THROTTLE_THRESHOLD);
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
        synchronized (throttledFutures) {
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
                    if (throttledFuture != null) {
                        throttledFuture.cancel(false);
                        if (throttledFuture.isCancelled()) {
                            throttledFutures.remove(higherKey);
                        }
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

    private @Nullable Object resultVoidOrFuture(@NotNull MethodSignature signature, ThrottledFuture<Object> future)
            throws IllegalCallerException {
        Class<?> returnType = signature.getReturnType();
        if (returnType.isAssignableFrom(ThrottledFuture.class)) {
            return future;
        }
        if (Void.TYPE.equals(returnType) || Void.class.equals(returnType)) {
            return null;
        }
        throw new ThrottleAspectException("Invalid return type for " + signature + " annotated with @Debounce, only Future or void allowed!");
    }


    @SuppressWarnings({"unchecked"})
    private @NotNull String constructIdentifier(JoinPoint joinPoint, String expression) throws ThrottleAspectException {
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
            throw new ThrottleAspectException("The expression: '" + expression + "' has not been resolved to a Collection<Object> or String", e);
        }
    }

    @Override
    public @NotNull Collection<LongRunningTask> getTasks() {
        synchronized (throttledFutures) {
            return List.copyOf(throttledFutures.values());
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
    protected static class Identifier extends Pair.Comparable<String, String> {

        public Identifier(String group, String identifier) {
            super(group, identifier);
        }

        public String getGroup() {
            return this.getFirst();
        }

        public String getIdentifier() {
            return this.getSecond();
        }

        public boolean hasGroupPrefix(String group) {
            return this.getGroup().indexOf(group) == 0;
        }

        @Override
        public String toString() {
            return "ThrottleAspect.Identifier{group='" + getGroup() + "',identifier='" + getIdentifier() + "'}";
        }
    }
}
