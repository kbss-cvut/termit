package cz.cvut.kbss.termit.validation;

import cz.cvut.kbss.termit.model.util.HasIdentifier;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Validates that the specified fields do not contain the same {@link HasIdentifier} value(s).
 * <p>
 * Each field may be either a singular {@link HasIdentifier} or a {@link Collection} of {@link HasIdentifier}. A value
 * is considered a duplicate if the same identifier ({@link HasIdentifier#getUri()}) appears in more than one of the
 * validated fields.
 */
public class DisjointReferencesValidator implements ConstraintValidator<Disjoint, Object> {

    private FieldConstraintExecutor executor;

    @Override
    public void initialize(Disjoint constraintAnnotation) {
        this.executor = new FieldConstraintExecutor(Disjoint.class, constraintAnnotation.value());
    }

    @Override
    public boolean isValid(Object bean, ConstraintValidatorContext ctx) {
        final Set<URI> seen = new HashSet<>();
        return executor.validate(bean, ctx, (fieldName, value, c) -> {
            boolean fieldValid = true;
            for (URI uri : extractUris(value)) {
                if (!seen.add(uri)) {
                    FieldConstraintExecutor.reportViolation(c, fieldName);
                    fieldValid = false;
                }
            }
            return fieldValid;
        });
    }

    private static Set<URI> extractUris(Object value) {
        if (value == null) {
            return Set.of();
        }
        if (value instanceof HasIdentifier hi) {
            return hi.getUri() != null ? Set.of(hi.getUri()) : Set.of();
        }
        if (value instanceof Collection<?> col) {
            final Set<URI> result = new HashSet<>();
            for (Object item : col) {
                if (item instanceof HasIdentifier hi && hi.getUri() != null) {
                    result.add(hi.getUri());
                }
            }
            return result;
        }
        throw new IllegalArgumentException(
                "Disjoint validation supports only HasIdentifier or Collection<HasIdentifier> fields, got "
                        + value.getClass());
    }
}
