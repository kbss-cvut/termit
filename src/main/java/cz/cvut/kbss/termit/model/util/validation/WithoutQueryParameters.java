package cz.cvut.kbss.termit.model.util.validation;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;

/**
 * Validates that a {@link java.net.URI} does not contain query parameters
 */
@Target({METHOD, FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = WithoutQueryParametersValidator.class)
public @interface WithoutQueryParameters {

    String message() default "URI must not contain query parameters.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
