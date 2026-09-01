package cz.cvut.kbss.termit.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that the values of the specified fields are disjoint.
 * <p>
 * It is expected the values of the fields are either individual instances of
 * {@link cz.cvut.kbss.termit.model.util.HasIdentifier} or collections of
 * {@link cz.cvut.kbss.termit.model.util.HasIdentifier} instances.
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DisjointReferencesValidator.class)
@Documented
@Repeatable(Disjoint.List.class)
public @interface Disjoint {

    String message() default "{jakarta.validation.constraints.Disjoint.message}";

    /**
     * @return Array of field names whose values (of type {@link cz.cvut.kbss.termit.model.util.HasIdentifier} or
     * {@link java.util.Set} of them) must be disjoint.
     */
    String[] value();

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    @Target({ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @interface List {
        Disjoint[] value();
    }
}
