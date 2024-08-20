package cz.cvut.kbss.termit.util.throttle;

import org.jetbrains.annotations.NotNull;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that calls to this method will be throttled & debounced.
 * Meaning that the action will be executed on the first call of the method,
 * then every next call which comes earlier then {@link ThrottleAspect#THROTTLE_THRESHOLD}
 * will return a pending future which might be resolved by a newer future.
 * Futures will be resolved once per {@link ThrottleAspect#THROTTLE_THRESHOLD} (+ duration to execute the future task).
 * <p>
 * Every annotated method should be tested for throttling to ensure it has the desired effect.
 * <p>
 * Method can't use any parameters that are part of persistent context, they need to be re-requested.
 * <p>
 * Available only for methods returning {@code void}, {@link Void} and {@link ThrottledFuture},
 * method signature may be {@link java.util.concurrent.Future},
 * but the returned concrete object has to be {@link ThrottledFuture}, <b>method call will throw otherwise!</b>
 * <p>
 * Note that returned future can be canceled (see {@link #clearGroup()})
 * <p>
 * Example implementation:
 * <pre>
 *  &#64;Throttle(value = "{paramObj, anotherParam}")
 *  public Future&lt;String&gt; myFunction(Object paramObj, Object anotherParam) {
 *      return ThrottledFuture.of(() -> doStuff());
 *  }
 * </pre>
 *
 * @implNote The annotation is being processed by {@link ThrottleAspect#throttledThreads}
 * @see <a href="https://css-tricks.com/debouncing-throttling-explained-examples/">Debouncing and Throttling</a>
 * @see <a href="https://github.com/kbss-cvut/termit/blob/master/doc/throttle-debounce.png">Throttling + debouncing image</a>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Throttle {

    /**
     * @return The Spring-EL expression returning a List of Objects which will be used to construct the unique identifier
     * for this throttled instance. In the expression, you have available method parameters.
     */
    @NotNull String value() default "";

    /**
     * @return The group identifier to which this throttle belongs to.
     * Used for canceling tasks with {@link #clearGroup()}.
     * When there is a pending task with a group that is also a prefix for this group, this task will be canceled immediately.
     */
    @NotNull String group() default "";

    /**
     * @return A prefix of a group that will be cleared on throttling.
     * All pending tasks with a prefix of this value will be canceled in favor of this task.
     */
    @NotNull String clearGroup() default "";
}
