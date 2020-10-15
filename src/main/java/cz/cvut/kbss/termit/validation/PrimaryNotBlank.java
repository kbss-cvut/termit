package cz.cvut.kbss.termit.validation;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.*;

/**
 * Validation constraint ensuring that a {@link cz.cvut.kbss.jopa.model.MultilingualString} contains a non-empty value
 * in the primary language configured for the application.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MultilingualStringPrimaryNotBlankValidator.class)
@Documented
@Repeatable(PrimaryNotBlank.List.class)
public @interface PrimaryNotBlank {

    String message() default "{javax.validation.constraints.NotBlank.message}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    @Target({ElementType.FIELD, ElementType.PARAMETER})
    @Retention(RetentionPolicy.RUNTIME)
    @Documented
    @interface List {
        PrimaryNotBlank[] value();
    }
}
