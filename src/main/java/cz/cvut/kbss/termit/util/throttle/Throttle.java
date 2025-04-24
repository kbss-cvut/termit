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

import cz.cvut.kbss.termit.util.Constants;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.Future;

/**
 * Indicates that calls to this method will be throttled & debounced.
 * <p>
 * The task created from the method will be executed on the first call of the method,
 * then every next call which comes earlier than {@link Constants#THROTTLE_THRESHOLD}
 * will return a pending future which might be resolved by a newer call.
 * Future will be resolved once per {@link Constants#THROTTLE_THRESHOLD} (+ duration to execute the future).
 * <p>
 * <img src="https://github.com/kbss-cvut/termit/tree/master/doc/throttle-debounce.png?raw=true" />
 * <p>
 * Call to this method cannot be part of an existing transaction.
 * If {@link org.springframework.transaction.annotation.Transactional @Transactional} is present with this annotation,
 * new transaction is created for the task execution.
 * <p>
 * Available only for methods returning {@code void}, {@link Void} and {@link ThrottledFuture},
 * method signature may be {@link Future},
 * or another type assignable from {@link ThrottledFuture},
 * but the returned concrete object has to be {@link ThrottledFuture}, <b>method call will throw otherwise!</b>
 * <p>
 * Whole body of method with {@code void} or {@link Void} return types will be considered as task which will be executed later.
 * In case of {@link Future} return type, only task in returned {@link ThrottledFuture} is throttled,
 * meaning that actual body of the method will be executed every call.
 * <p>
 * Note that returned future can be canceled
 * <p>
 * Method may also return already canceled or fulfilled future; in that case, the result is returned immediately.
 * <p>
 * Example implementation:
 * <pre><code>
 *  {@code @}Throttle(value = "{#paramObj, #anotherParam}")
 *  public Future&lt;String&gt; myFunction(Object paramObj, Object anotherParam) {
 *      // this will execute on every call as the return type is future
 *      LOG.trace("my function called");
 *      return ThrottledFuture.of(() -> doStuff()); // doStuff() will be throttled
 *  }
 * </code></pre>
 * <pre><code>
 *  {@code @}Throttle(value = "{#paramObj, #anotherParam}")
 *  public void myFunction(Object paramObj, Object anotherParam) {
 *      // whole method body will be throttled, as return type is not future
 *      LOG.trace("my function called");
 *  }
 * </code></pre>
 *
 * @implNote Methods will be called from a separated thread.
 * @see <a href="https://css-tricks.com/debouncing-throttling-explained-examples/">Debouncing and Throttling</a>
 * @see <a href="https://github.com/kbss-cvut/termit/blob/master/doc/throttle-debounce.png">Throttling + debouncing image</a>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Throttle {

    /**
     * The Spring-EL expression
     * returning a List of Objects or a String which will be used to construct the unique identifier
     * for this throttled instance.
     */
    @Nonnull String value() default "";

    /**
     * The Spring-EL expression
     * returning group identifier a List of Objects or a String to which this throttle belongs.
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
     * @see String#compareTo(String)
     */
    @Nonnull String group() default "";

    /**
     * @return a key name of the task which is displayed on the frontend.
     * Example: {@code name = "validation"} on frontend a translatable name with a key
     * {@code "longrunningtasks.name.validation"} is displayed.
     * Leave blank to hide the task on the frontend.
     */
    @Nullable String name() default "";
}
