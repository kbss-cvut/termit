package cz.cvut.kbss.termit.util.throttle;

import cz.cvut.kbss.termit.exception.TermItException;
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
import java.time.Duration;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_SINGLETON;

/**
 * @implNote The aspect is configured in {@code spring-aop.xml}, this uses Spring AOP instead of AspectJ.
 */
@Order
@Scope(SCOPE_SINGLETON)
@Component("throttleAspect")
@Profile("!test")
public class ThrottleAspect {

    public static final Duration THROTTLE_THRESHOLD = Duration.ofSeconds(10); // TODO: config value

    private static final Logger LOG = LoggerFactory.getLogger(ThrottleAspect.class);

    private final Map<String, ThrottledFuture<Object>> throttledFutures;

    private final Map<String, Instant> lastRun;

    private final NavigableMap<String, Future<Object>> scheduledFutures;

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

    public ThrottleAspect(Map<String, ThrottledFuture<Object>> throttledFutures, Map<String, Instant> lastRun,
                          NavigableMap<String, Future<Object>> scheduledFutures, TaskScheduler taskScheduler,
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

    /**
     * Use this method with caution, ensure that both throttled futures contain the same type!
     */
    @SuppressWarnings({"unchecked"})
    private static <T> ThrottledFuture<Object> transferTask(@NotNull ThrottledFuture<T> source,
                                                            @NotNull ThrottledFuture<T> target) {
        return (ThrottledFuture<Object>) source.transfer(target);
    }

    private @NotNull AbstractMap.SimpleImmutableEntry<Runnable, ThrottledFuture<Object>> getFutureTask(
            @NotNull ProceedingJoinPoint joinPoint, String key, @NotNull ThrottledFuture<Object> future)
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
            ThrottledFuture<Object> throttledMethodFuture = (ThrottledFuture<Object>) joinPoint.proceed();
            // future acquired by key or a new future supplied, ensuring the same type
            // ThrottledFuture#updateOther will create a new future when required
            throttledFuture = transferTask(throttledMethodFuture, future);
        } else {
            future.update(() -> {
                try {
                    return joinPoint.proceed();
                } catch (Throwable e) {
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
            LOG.trace("Running throttled task '{}'", key);
            // restore the security context
            SecurityContextHolder.setContext(securityContext.get());
            try {
                // update last run timestamp
                synchronized (lastRun) {
                    lastRun.put(key, Instant.now(clock));
                }
                // fulfill the future
                future.run();
            } finally {
                // clear the security context
                SecurityContextHolder.clearContext();
                LOG.trace("Throttled task run finished '{}'", key);
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
                                                            @NotNull Throttle throttleAnnotation)
            throws Throwable {

        // if the current thread is already executing a throttled code, we want to skip further throttling
        if (throttledThreads.contains(Thread.currentThread().getId())) {
            // proceed with method execution
            Object result = joinPoint.proceed();
            if (result instanceof ThrottledFuture<?> throttledFuture) {
                // directly run throttled future
                throttledFuture.run();
                return throttledFuture;
            }
            return result;
        }

        final MethodSignature signature = (MethodSignature) joinPoint.getSignature();

        // construct the throttle instance key
        final String key = makeKey(joinPoint, throttleAnnotation);
        LOG.trace("Throttling task with key '{}'", key);

        Map.Entry<String, Future<Object>> ceiling = scheduledFutures.higherEntry(key);
        if (!throttleAnnotation.group().isBlank() && ceiling != null) {
            Future<Object> ceilingFuture = ceiling.getValue();
            if (!ceilingFuture.isDone() && !ceilingFuture.isCancelled()) {
                LOG.trace("Throttling canceled due to scheduled ceiling task '{}'", ceiling.getKey());
                return ThrottledFuture.canceled();
            }
        }

        // if there is a scheduled task and this throttled instance was executed in the last THROTTLE_THRESHOLD
        // cancel the scheduled task
        // -> the execution is further delayed
        Future<Object> oldFuture = scheduledFutures.get(key);
        boolean throttleNotExpired = lastRun.getOrDefault(key, Instant.EPOCH)
                                            .isAfter(Instant.now(clock).minus(THROTTLE_THRESHOLD));
        if (oldFuture != null && throttleNotExpired) {
            oldFuture.cancel(false);
        }

        // acquire a throttled future from a map, or make a new one
        ThrottledFuture<Object> oldThrottledFuture = throttledFutures.getOrDefault(key, new ThrottledFuture<>());

        AbstractMap.SimpleImmutableEntry<Runnable, ThrottledFuture<Object>> entry = getFutureTask(joinPoint, key, oldThrottledFuture);
        Runnable task = entry.getKey();
        ThrottledFuture<Object> future = entry.getValue();
        // update the throttled future in the map
        throttledFutures.put(key, future);

        Object result = voidOrFuture(signature, key, future);

        clearGroup(throttleAnnotation);

        if (oldFuture == null || oldFuture.isDone() || oldFuture.isCancelled()) {
            Future<Object> scheduled = (Future<Object>) taskScheduler.schedule(task, Instant.now(clock)
                                                                                            .plus(THROTTLE_THRESHOLD));
            scheduledFutures.put(key, scheduled);
        }

        return result;
    }

    private void clearGroup(Throttle throttleAnnotation) {
        if (!throttleAnnotation.clearGroup().isBlank()) {
            Map<String, Future<Object>> toClear = scheduledFutures.tailMap(throttleAnnotation.clearGroup());
            toClear.forEach((k, f) -> f.cancel(false));
            toClear.clear();
        }
    }

    private String makeKey(JoinPoint joinPoint, Throttle throttleAnnotation) throws IllegalCallerException {
        final String identifier = constructIdentifier(joinPoint, throttleAnnotation.value());
        final String groupIdentifier = throttleAnnotation.group();

        if (identifier == null) {
            throw new IllegalCallerException("Identifier in Debounce annotation resolved to null");
        }

        return groupIdentifier + "-" + joinPoint.getSignature().toShortString() + "-" + identifier;
    }

    private @Nullable Object voidOrFuture(@NotNull MethodSignature signature, String key,
                                          ThrottledFuture<Object> future)
            throws IllegalCallerException {
        Class<?> returnType = signature.getReturnType();
        if (returnType.isAssignableFrom(Future.class)) {
            return future;
        }
        if (Void.TYPE.equals(returnType) || Void.class.equals(returnType)) {
            return null;
        }
        throw new IllegalCallerException("Invalid return type for " + signature + " annotated with @Debounce, only Future or void allowed!");
    }

    private @Nullable String constructIdentifier(JoinPoint joinPoint, String expression) {
        if (expression == null || expression.isBlank()) {
            return "";
        }

        final Map<String, Object> parameters = new HashMap<>();
        resolveParameters(parameters, (MethodSignature) joinPoint.getSignature(), joinPoint);
        assert !parameters.isEmpty();

        final EvaluationContext context = makeContext(joinPoint, parameters);

        final List<Object> identifier = parser.parseExpression(expression).getValue(context, List.class);
        assert identifier != null;

        return identifier.stream().map(Object::toString).collect(Collectors.joining("-"));
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
}
