package cz.cvut.kbss.termit.util.debounce;

import cz.cvut.kbss.termit.exception.TermItException;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_SINGLETON;

/**
 * @implNote The aspect is configured in {@code spring-aop.xml}, this uses Spring AOP instead of AspectJ.
 */
@Order
@Scope(SCOPE_SINGLETON)
@Component("debounceAspect")
public class DebounceAspect {

    private static final Logger LOG = LoggerFactory.getLogger(DebounceAspect.class);

    private static final NavigableMap<String, Future<?>> store = new ConcurrentSkipListMap<>();

    private static final Set<Long> debouncedThreads = ConcurrentHashMap.newKeySet();

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final ExpressionParser parser = new SpelExpressionParser();

    private final TaskScheduler taskScheduler;

    @Autowired
    public DebounceAspect(TaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
    }

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

    private static @NotNull FutureTask<Object> getFutureTask(@NotNull ProceedingJoinPoint joinPoint, String key) {
        final Supplier<SecurityContext> securityContext = SecurityContextHolder.getDeferredContext();
        final Class<?> returnType = ((MethodSignature) joinPoint.getSignature()).getReturnType();
        final boolean resolveFuture = returnType.isAssignableFrom(Future.class);

        return new FutureTask<>(() -> {
            final Long threadId = Thread.currentThread().getId();
            debouncedThreads.add(threadId);
            LOG.trace("Running debounced task {}", key);
            SecurityContextHolder.setContext(securityContext.get());
            try {
                if (resolveFuture) {
                    Future<?> future = (Future<?>) joinPoint.proceed();
                    if (future instanceof Runnable runnable) {
                        runnable.run();
                    }
                    return future.get();
                } else {
                    return joinPoint.proceed();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            } catch (Throwable e) {
                throw new TermItException(e);
            } finally {
                SecurityContextHolder.clearContext();
                LOG.trace("Debounced task run finished {}", key);
                debouncedThreads.remove(threadId);
            }
        });
    }

    /**
     * @return future or null
     * @throws TermItException        when the target method throws
     * @throws IllegalCallerException when the annotated method returns another type than {@code void}, {@link Void} or {@link Future}
     * @implNote Around advice configured in {@code spring-aop.xml}
     */
    @SuppressWarnings("unused")
    public @Nullable Object debounceMethodCall(@NotNull ProceedingJoinPoint joinPoint,
                                               @NotNull Debounce debounceAnnotation)
            throws Throwable {

        if (debouncedThreads.contains(Thread.currentThread().getId())) {
            return joinPoint.proceed();
        }

        final MethodSignature signature = (MethodSignature) joinPoint.getSignature();

        final String key = makeKey(joinPoint, debounceAnnotation);
        LOG.trace("Debouncing task with key {}", key);

        if (!debounceAnnotation.clearGroup().isBlank()) {
            store.tailMap(debounceAnnotation.clearGroup()).clear();
        }

        FutureTask<?> task = getFutureTask(joinPoint, key);

        Object result = voidOrFuture(signature, task);

        if (store.containsKey(key)) {
            boolean canceled = store.get(key).cancel(false);
            if (canceled) {
                LOG.trace("Old task canceled {}", key);
            }
        }

        store.put(key, taskScheduler.schedule(task, Instant.now().plus(TIMEOUT)));

        return result;
    }

    private String makeKey(JoinPoint joinPoint, Debounce debounceAnnotation) throws IllegalCallerException {
        final String identifier = constructIdentifier(joinPoint, debounceAnnotation.value());
        final String groupIdentifier = debounceAnnotation.group();

        if (identifier == null) {
            throw new IllegalCallerException("Identifier in Debounce annotation resolved to null");
        }

        return groupIdentifier + "-" + joinPoint.getSignature() + "-" + identifier;
    }

    private @Nullable Object voidOrFuture(@NotNull MethodSignature signature, FutureTask<?> task)
            throws IllegalCallerException {
        Class<?> returnType = signature.getReturnType();
        if (returnType.isAssignableFrom(Future.class)) {
            return task;
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

        final List<?> identifier = parser.parseExpression(expression).getValue(context, List.class);
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
        public boolean canRead(@NotNull EvaluationContext context, Object target, @NotNull String name)
                throws AccessException {
            return map.containsKey(name);
        }

        @Override
        public @NotNull TypedValue read(@NotNull EvaluationContext context, Object target, @NotNull String name)
                throws AccessException {
            return new TypedValue(map.get(name));
        }

        @Override
        public boolean canWrite(@NotNull EvaluationContext context, Object target, @NotNull String name)
                throws AccessException {
            return false;
        }

        @Override
        public void write(@NotNull EvaluationContext context, Object target, @NotNull String name, Object newValue)
                throws AccessException {
            throw new AccessException("Unsupported operation");
        }
    }
}
