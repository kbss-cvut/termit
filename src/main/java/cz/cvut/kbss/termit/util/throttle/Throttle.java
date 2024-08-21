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
 * or another type assignable from {@link ThrottledFuture},
 * but the returned concrete object has to be {@link ThrottledFuture}, <b>method call will throw otherwise!</b>
 * <p>
 * Note that returned future can be canceled (see {@link #clearGroup()})
 * <p>
 * Example implementation:
 * <pre><code>
 *  {@code @}Throttle(value = "{paramObj, anotherParam}")
 *  public Future&lt;String&gt; myFunction(Object paramObj, Object anotherParam) {
 *      return ThrottledFuture.of(() -> doStuff());
 *  }
 * </code></pre>
 *
 * @implNote The annotation is being processed by {@link ThrottleAspect#throttledThreads}
 * @see <a href="https://css-tricks.com/debouncing-throttling-explained-examples/">Debouncing and Throttling</a>
 * @see <a href="https://github.com/kbss-cvut/termit/blob/master/doc/throttle-debounce.png">Throttling + debouncing image</a>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Throttle {

    /**
     * The Spring-EL expression returning a List of Objects which will be used to construct the unique identifier
     * for this throttled instance.
     * <p>
     * In the expression, you have available method parameters.
     */
    @NotNull String value() default "";

    /**
     * The Spring-EL expression returning group identifier (String) to which this throttle belongs.
     * <p>
     * When there is a pending task <code>P</code> with a group
     * that is also a prefix for a group of a new task <code>N</code>,
     * the new task <code>N</code> will be canceled immediately.
     * The group of the task <code>P</code> is lower than the group of the task <code>N</code>.
     * <p>
     * When a task with lower group is scheduled, all scheduled tasks with higher groups are canceled.
     * <p>
     * Example:
     * <pre>
     *     new task A with group <code>"my.group.task1"</code> is scheduled
     *     new task B with group <code>"my.group.task1.subtask"</code> wants to be scheduled
     *        -&gt; task <b>B is canceled</b> immediately (task A with lower group is already pending)
     *     new task C with group <code>"my.group"</code> is scheduled
     *        -&gt; task <b>A is canceled</b> as the task C has lower group than A
     * </pre>
     * Blank string disables any group processing.
     */
    @NotNull String group() default "";
}
