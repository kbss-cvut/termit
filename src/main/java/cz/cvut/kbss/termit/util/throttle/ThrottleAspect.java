package cz.cvut.kbss.termit.util.throttle;

import cz.cvut.kbss.termit.exception.TermItException;
import cz.cvut.kbss.termit.exception.ThrottleAspectException;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.core.annotation.Order;
import org.springframework.expression.AccessException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.DataBindingMethodResolver;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static cz.cvut.kbss.termit.util.Constants.THROTTLE_THRESHOLD;
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_SINGLETON;

/**
 * @implNote The aspect is configured in {@code spring-aop.xml}, this uses Spring AOP instead of AspectJ.
 */
@Order
@Scope(SCOPE_SINGLETON)
@Component("throttleAspect")
@Profile("!test")
public class ThrottleAspect {

    private static final Logger LOG = LoggerFactory.getLogger(ThrottleAspect.class);

    /**
     * group, identifier -> future
     */
    private final Map<Identifier, ThrottledFuture<Object>> throttledFutures;

    private final Map<Identifier, Instant> lastRun;

    /**
     * group, identifier -> future
     */
    private final NavigableMap<Identifier, Future<Object>> scheduledFutures;

    /**
     * synchronize before access
     */
    private final Set<Long> throttledThreads = new HashSet<>();

    private final ExpressionParser parser = new SpelExpressionParser();

    private final TaskScheduler taskScheduler;

    private final Clock clock;

    @Autowired
    public ThrottleAspect(TaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
        throttledFutures = new HashMap<>();
        lastRun = new HashMap<>();
        scheduledFutures = new TreeMap<>();
        clock = Clock.systemUTC(); // used by Instant.now() by default
    }

    protected ThrottleAspect(Map<Identifier, ThrottledFuture<Object>> throttledFutures,
                             Map<Identifier, Instant> lastRun,
                             NavigableMap<Identifier, Future<Object>> scheduledFutures, TaskScheduler taskScheduler,
                             Clock clock) {
        this.throttledFutures = throttledFutures;
        this.lastRun = lastRun;
        this.scheduledFutures = scheduledFutures;
        this.taskScheduler = taskScheduler;
        this.clock = clock;
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

        for (int i = 0; i < params.length; i++) {
            map.putIfAbsent(paramNames[i], params[i]);
        }
    }

    private static EvaluationContext makeContext(JoinPoint joinPoint, Map<String, Object> parameters) {
        return SimpleEvaluationContext.forPropertyAccessors(new MapPropertyAccessor<>(joinPoint.getTarget()
                                                                                               .getClass(), parameters))
                                      .withMethodResolvers(DataBindingMethodResolver.forInstanceMethodInvocation())
                                      .withRootObject(joinPoint.getTarget()).build();
    }

    @SuppressWarnings("unchecked")
    private static ThrottledFuture<Object> transferTask(@NotNull ThrottledFuture<?> source,
                                                        @NotNull ThrottledFuture<?> target) {
        // casting the type parameter to Object
        return ((ThrottledFuture<Object>) source).transfer((ThrottledFuture<Object>) target);
    }

    private @NotNull AbstractMap.SimpleImmutableEntry<Runnable, ThrottledFuture<Object>> getFutureTask(
            @NotNull ProceedingJoinPoint joinPoint, Identifier identifier, @NotNull ThrottledFuture<Object> future)
            throws Throwable {

        final Supplier<SecurityContext> securityContext = SecurityContextHolder.getDeferredContext();
        final Class<?> returnType = ((MethodSignature) joinPoint.getSignature()).getReturnType();
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
                throttledFuture = transferTask(throttledMethodFuture, future);
            } else {
                throw new ThrottleAspectException("Returned value is not a ThrottledFuture");
            }
        } else {
            future.update(() -> {
                try {
                    return joinPoint.proceed();
                } catch (Throwable e) {
                    // exception happened inside throttled method
                    throw new TermItException(e);
                }
            });
        }

        // create a task which will be scheduled with executor
        final Runnable toSchedule = () -> {
            if (future.isCancelled() || future.isDone()) {
                return;
            }
            // mark the thread as throttled
            final Long threadId = Thread.currentThread().getId();
            throttledThreads.add(threadId);
            LOG.trace("Running throttled task '{}'", identifier);
            // restore the security context
            SecurityContextHolder.setContext(securityContext.get());
            try {
                // update last run timestamp
                synchronized (lastRun) {
                    lastRun.put(identifier, Instant.now(clock));
                }
                // fulfill the future
                future.run();
            } finally {
                // clear the security context
                SecurityContextHolder.clearContext();
                LOG.trace("Throttled task run finished '{}'", identifier);
                // remove throttled mark
                throttledThreads.remove(threadId);
            }
        };

        return new AbstractMap.SimpleImmutableEntry<>(toSchedule, throttledFuture);
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

        if (!throttleAnnotation.group().isBlank()) {
            // check if there is a task with higher group
            // and if so, cancel this task in favor of the higher group
            final Map.Entry<Identifier, Future<Object>> lowerEntry = scheduledFutures.lowerEntry(identifier);
            if (lowerEntry != null) {
                final Future<Object> lowerFuture = lowerEntry.getValue();
                boolean hasGroupPrefix = identifier.hasGroupPrefix(lowerEntry.getKey().getGroup());
                if (hasGroupPrefix && !lowerFuture.isDone() && !lowerFuture.isCancelled()) {
                    LOG.trace("Throttling canceled due to scheduled lower task '{}'", lowerEntry.getKey());
                    return ThrottledFuture.canceled();
                }
            }

            cancelWithLowerGroup(throttleAnnotation);
        }

        // if there is a scheduled task and this throttled instance was executed in the last THROTTLE_THRESHOLD
        // cancel the scheduled task
        // -> the execution is further delayed
        Future<Object> oldFuture = scheduledFutures.get(identifier);
        boolean throttleNotExpired = lastRun.getOrDefault(identifier, Instant.EPOCH)
                                            .isAfter(Instant.now(clock).minus(THROTTLE_THRESHOLD));
        if (oldFuture != null && throttleNotExpired) {
            oldFuture.cancel(false);
        }

        // acquire a throttled future from a map, or make a new one
        ThrottledFuture<Object> oldThrottledFuture = throttledFutures.getOrDefault(identifier, new ThrottledFuture<>());

        AbstractMap.SimpleImmutableEntry<Runnable, ThrottledFuture<Object>> entry = getFutureTask(joinPoint, identifier, oldThrottledFuture);
        Runnable task = entry.getKey();
        ThrottledFuture<Object> future = entry.getValue();
        // update the throttled future in the map
        throttledFutures.put(identifier, future);

        Object result = resultVoidOrFuture(signature, future);

        if (oldFuture == null || oldFuture.isDone() || oldFuture.isCancelled()) {
            schedule(identifier, task);
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private void schedule(Identifier identifier, Runnable task) {
        Future<?> scheduled = taskScheduler.schedule(task, Instant.now(clock).plus(THROTTLE_THRESHOLD));
        // casting the type parameter to Object
        scheduledFutures.put(identifier, (Future<Object>) scheduled);
    }

    private void cancelWithLowerGroup(Throttle throttleAnnotation) {
        // look for any futures with lower group
        // cancel them and remove from maps
        Future<Object> higherFuture;
        Identifier higherKey = scheduledFutures.higherKey(new Identifier(throttleAnnotation.group(), ""));
        while (higherKey != null) {
            if (!higherKey.hasGroupPrefix(throttleAnnotation.group()) || higherKey.getGroup()
                                                                                  .equals(throttleAnnotation.group())) {
                break;
            }

            higherFuture = scheduledFutures.get(higherKey);
            higherFuture.cancel(false);
            final ThrottledFuture<Object> throttledFuture = throttledFutures.get(higherKey);
            if (throttledFuture != null) {
                throttledFuture.cancel(false);
            }

            scheduledFutures.remove(higherKey);
            throttledFutures.remove(higherKey);

            higherKey = scheduledFutures.higherKey(higherKey);
        }
    }

    private Identifier makeIdentifier(JoinPoint joinPoint, Throttle throttleAnnotation) throws IllegalCallerException {
        final String identifier = constructIdentifier(joinPoint, throttleAnnotation.value());
        final String groupIdentifier = throttleAnnotation.group();

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
        assert !parameters.isEmpty();

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
            throw new ThrottleAspectException("The identifier expression: '" + expression + "' has not been resolved to a Collection<Object>", e);
        }

    }

    /**
     * Resolves properties to map values
     *
     * @param rootClass a class this accessor should accept as target's class
     * @param map       the map with values
     */
    private record MapPropertyAccessor<T>(Class<?> rootClass, Map<String, T> map) implements PropertyAccessor {

        @Override
        public Class<?>[] getSpecificTargetClasses() {
            return new Class<?>[]{rootClass, map.getClass()};
        }

        @Override
        public boolean canRead(@NotNull EvaluationContext context, Object target, @NotNull String name) {
            return map.containsKey(name);
        }

        @Override
        public @NotNull TypedValue read(@NotNull EvaluationContext context, Object target, @NotNull String name) {
            return new TypedValue(map.get(name));
        }

        @Override
        public boolean canWrite(@NotNull EvaluationContext context, Object target, @NotNull String name) {
            return false;
        }

        @Override
        public void write(@NotNull EvaluationContext context, Object target, @NotNull String name, Object newValue)
                throws AccessException {
            throw new AccessException("Unsupported operation");
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
    protected static class Identifier extends ComparablePair<String, String> {

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
