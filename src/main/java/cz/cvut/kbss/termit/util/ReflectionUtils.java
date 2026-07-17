package cz.cvut.kbss.termit.util;

import java.lang.annotation.Annotation;
import java.util.Objects;
import java.util.Optional;

public class ReflectionUtils {

    private ReflectionUtils() {
        throw new AssertionError();
    }

    /**
     * Resolves the class annotated with the specified annotation for the given instance.
     *
     * @param instance   Object whose class is to be checked for the annotation
     * @param annotation Annotation to look for.
     * @return Class annotated with the specified annotation or empty if no such class is found
     */
    public static Optional<Class<?>> findAnnotatedClass(Object instance, Class<? extends Annotation> annotation) {
        Objects.requireNonNull(instance);
        Objects.requireNonNull(annotation);

        Class<?> beanClass = instance.getClass();
        while (beanClass != null && beanClass != Object.class) {
            // Use getAnnotationsByType to correctly detect @Repeatable annotations, which are otherwise
            // wrapped in their container annotation and not visible to isAnnotationPresent.
            if (beanClass.getAnnotationsByType(annotation).length > 0) {
                return Optional.of(beanClass);
            }
            beanClass = beanClass.getSuperclass();
        }
        return Optional.empty();
    }
}
