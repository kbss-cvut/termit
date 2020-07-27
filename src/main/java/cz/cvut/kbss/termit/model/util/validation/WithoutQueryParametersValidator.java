package cz.cvut.kbss.termit.model.util.validation;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.net.URI;

/**
 * Validates that a URI does not contain query parameters.
 */
public class WithoutQueryParametersValidator implements ConstraintValidator<WithoutQueryParameters, URI> {

    @Override
    public boolean isValid(URI uri, ConstraintValidatorContext constraintValidatorContext) {
        if (uri == null) {
            return true;
        }
        final String strUri = uri.toString();
        return !strUri.contains("?") && !strUri.contains("&");
    }
}
