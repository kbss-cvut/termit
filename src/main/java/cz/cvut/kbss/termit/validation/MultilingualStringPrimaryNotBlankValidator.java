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

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.termit.exception.TermItException;
import cz.cvut.kbss.termit.model.util.validation.HasPrimaryLanguage;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Validates that a {@link MultilingualString} contains translation in the primary language of the entity.
 * <p>
 * The entity must implement {@link HasPrimaryLanguage} to provide the primary language.
 */
public class MultilingualStringPrimaryNotBlankValidator
        implements ConstraintValidator<PrimaryNotBlank, HasPrimaryLanguage> {
    /// cache for reflective operations
    private static final ConcurrentMap<Class<?>, List<VarHandle>> CACHE = new ConcurrentHashMap<>();

    private MethodHandles.Lookup lookup = MethodHandles.lookup();
    private String[] fieldNames;

    @Override
    public void initialize(PrimaryNotBlank constraintAnnotation) {
        fieldNames = constraintAnnotation.value();
    }

    @Override
    public boolean isValid(HasPrimaryLanguage beanToValidate,
                           ConstraintValidatorContext constraintValidatorContext) {
        if (beanToValidate == null) {
            return false;
        }

        final Class<?> annotatedClass = findAnnotatedClass(beanToValidate);
        initializeLookup(annotatedClass);
        final List<VarHandle> fieldHandles = getFieldHandles(annotatedClass);
        int index = 0;
        for (final VarHandle field : fieldHandles) {
            final MultilingualString value = (MultilingualString) field.get(beanToValidate);
            if (!isValid(value, beanToValidate)) {
                constraintValidatorContext.disableDefaultConstraintViolation();
                constraintValidatorContext
                        .buildConstraintViolationWithTemplate(constraintValidatorContext.getDefaultConstraintMessageTemplate())
                        .addPropertyNode(fieldNames[index])
                        .addConstraintViolation();
                return false;
            }
            index++;
        }

        return true;
    }

    /**
     * Checks if the given {@link MultilingualString} contains a non-blank value for the primary language of the bean.
     * @param value the {@link MultilingualString} to validate
     * @param beanToValidate the bean whose primary language is used for validation
     * @return true if the value is not null and contains a non-blank value for the primary language,
     *         false otherwise
     */
    private boolean isValid(MultilingualString value, HasPrimaryLanguage beanToValidate) {
        if (value == null) {
            return false;
        }
        return value.contains(beanToValidate.getPrimaryLanguage()) &&
                !value.get(beanToValidate.getPrimaryLanguage()).trim().isEmpty();
    }

    /**
     * Resolves the class annotated with {@link PrimaryNotBlank} for the given bean.
     * @param beanToValidate Object whose class is to be checked for the annotation.
     * @return Class annotated with {@link PrimaryNotBlank}.
     */
    private static Class<?> findAnnotatedClass(HasPrimaryLanguage beanToValidate) {
        Class<?> beanClass = beanToValidate.getClass();
        while (beanClass != Object.class) {
            if (beanClass.isAnnotationPresent(PrimaryNotBlank.class)) {
                return beanClass;
            }
            beanClass = beanClass.getSuperclass();
        }
        throw new IllegalArgumentException("PrimaryNotBlank annotation not found on class " + beanToValidate.getClass());
    }


    /**
     * Retrieves a list of {@link VarHandle} instances for the fields specified in {@code fieldNames}
     * from the class annotated with {@link PrimaryNotBlank} for the given bean.
     * <p>
     * Uses a cache to avoid repeated reflective lookups.
     *
     * @return List of {@link VarHandle} for the specified fields.
     * @throws RuntimeException if a specified field is not found or not accessible.
     */
    private List<VarHandle> getFieldHandles(Class<?> annotatedClass) {
        return CACHE.computeIfAbsent(annotatedClass, beanClass ->
                Arrays.stream(fieldNames)
                      .map(fieldName -> {
                        try {
                            return lookup.findVarHandle(beanClass, fieldName, MultilingualString.class);
                        } catch (NoSuchFieldException | IllegalAccessException e) {
                            throw new IllegalArgumentException("Wrong Field PrimaryNotBlank annotation usage: " + fieldName + " not found in " + beanClass, e);
                        }
                      }).toList()
        );
    }

    private void initializeLookup(Class<?> annotatedClass) {
        try {
            lookup = MethodHandles.privateLookupIn(annotatedClass, lookup);
        } catch (IllegalAccessException e) {
            throw new TermItException(e);
        }
    }
}
