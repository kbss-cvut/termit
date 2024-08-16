package cz.cvut.kbss.termit.util.debounce;

import org.jetbrains.annotations.NotNull;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that calls to this method will be debounced.
 * <p>
 * Every annotated method should be tested for debouncing.
 * <p>
 * Method can't use any parameters that are part of persistent context, they need to be re-requested.
 * <p>
 * Available only for methods returning {@code void}, {@link Void} and {@link java.util.concurrent.Future}.
 * Example implementation:
 * <code>
 *  @Debounced("{paramObj}")
 *  public Future<String>
 * </code>
 * @see <a href="https://css-tricks.com/debouncing-throttling-explained-examples/">Debouncing and Throttling</a>
 * @implNote The annotation is being processed by {@link DebounceAspect#debounceMethodCall}
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Debounce {

    /**
     * @return The Spring-EL expression returning a List of Objects which will be used to construct the unique identifier
     * for this debounced instance. In the expression, you have available method parameters.
     */
    @NotNull String value() default "";

    /**
     * @return The group identifier to which this debounce belongs to
     */
    @NotNull String group() default "";

    /**
     * @return A prefix of a group that will be cleared on debouncing.
     * All pending tasks with a prefix of this value will be canceled in favor of this task.
     */
    @NotNull String clearGroup() default "";
}
