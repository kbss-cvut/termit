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
package cz.cvut.kbss.termit.validation;

import cz.cvut.kbss.termit.exception.TermItException;
import cz.cvut.kbss.termit.util.ReflectionUtils;
import jakarta.validation.ConstraintValidatorContext;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Reusable helper that drives field-oriented Bean Validation constraint validators.
 * <p>
 * Encapsulates the boilerplate common to constraint validators that operate on a set of named fields declared on a
 * class annotated with a marker annotation:
 * <ul>
 *     <li>resolving the annotated class in the bean's class hierarchy,</li>
 *     <li>initializing a {@link MethodHandles.Lookup} with private access to it,</li>
 *     <li>caching {@link VarHandle} instances for the listed fields,</li>
 *     <li>iterating over the fields, reading their values and delegating the actual check to the caller,</li>
 *     <li>and reporting property-level constraint violations.</li>
 * </ul>
 * The concrete check is supplied as a {@link FieldCheck}. Any cross-field state (e.g. accumulated identifiers)
 * is captured in the caller's closure.
 */
public class FieldConstraintExecutor {

    private static final ConcurrentMap<CacheKey, List<VarHandle>> CACHE = new ConcurrentHashMap<>();

    private final Class<? extends Annotation> annotationType;
    private final String[] fieldNames;

    private MethodHandles.Lookup lookup = MethodHandles.lookup();

    public FieldConstraintExecutor(Class<? extends Annotation> annotationType, String[] fieldNames) {
        this.annotationType = Objects.requireNonNull(annotationType);
        this.fieldNames = Objects.requireNonNull(fieldNames);
    }

    /**
     * Iterates over the configured fields of the specified bean, reads each field's value and passes it to the
     * supplied {@link FieldCheck}.
     * <p>
     * The result is the conjunction of all field checks; every check is executed even after a failure, so all
     * violations can be reported in a single validation pass.
     *
     * @param bean  Bean to validate; may be {@code null}
     * @param ctx   Constraint validator context (passed to {@link FieldCheck#test})
     * @param check Per-field check
     * @return {@code true} iff {@code bean} is {@code null} or every field check returns {@code true}
     */
    public boolean validate(Object bean, ConstraintValidatorContext ctx, FieldCheck check) {
        if (bean == null) {
            return true;
        }
        final Class<?> annotatedClass = findAnnotatedClass(bean);
        initializeLookup(annotatedClass);
        final List<VarHandle> handles = getFieldHandles(annotatedClass);

        boolean valid = true;
        for (int i = 0; i < handles.size(); i++) {
            final Object value = handles.get(i).get(bean);
            if (!check.test(fieldNames[i], value, ctx)) {
                valid = false;
            }
        }
        return valid;
    }

    /**
     * Reports a constraint violation on the specified property of the currently validated bean using the default
     * constraint message template.
     */
    public static void reportViolation(ConstraintValidatorContext ctx, String fieldName) {
        ctx.disableDefaultConstraintViolation();
        ctx.buildConstraintViolationWithTemplate(ctx.getDefaultConstraintMessageTemplate())
           .addPropertyNode(fieldName)
           .addConstraintViolation();
    }

    private Class<?> findAnnotatedClass(Object bean) {
        return ReflectionUtils.findAnnotatedClass(bean, annotationType).orElseThrow(
                () -> new IllegalArgumentException(
                        annotationType.getSimpleName() + " annotation not found on class " + bean.getClass()));
    }

    private void initializeLookup(Class<?> annotatedClass) {
        try {
            lookup = MethodHandles.privateLookupIn(annotatedClass, lookup);
        } catch (IllegalAccessException e) {
            throw new TermItException(e);
        }
    }

    private List<VarHandle> getFieldHandles(Class<?> annotatedClass) {
        return CACHE.computeIfAbsent(new CacheKey(annotatedClass, fieldNames), key ->
                Arrays.stream(key.fieldNames)
                      .map(fieldName -> resolveVarHandle(key.clazz, fieldName))
                      .toList());
    }

    private VarHandle resolveVarHandle(Class<?> beanClass, String fieldName) {
        try {
            final Class<?> fieldType = beanClass.getDeclaredField(fieldName).getType();
            return lookup.findVarHandle(beanClass, fieldName, fieldType);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalArgumentException(
                    "Wrong " + annotationType.getSimpleName() + " annotation usage: field '" + fieldName
                            + "' not found in " + beanClass, e);
        }
    }

    /**
     * Callback invoked for each configured field.
     */
    @FunctionalInterface
    public interface FieldCheck {
        /**
         * @return {@code true} if the field's value satisfies the constraint, {@code false} otherwise
         */
        boolean test(String fieldName, Object value, ConstraintValidatorContext ctx);
    }

    private record CacheKey(Class<?> clazz, String[] fieldNames) {
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof CacheKey other)) return false;
            return clazz.equals(other.clazz) && Arrays.equals(fieldNames, other.fieldNames);
        }

        @Override
        public int hashCode() {
            return Objects.hash(clazz, Arrays.hashCode(fieldNames));
        }
    }
}
