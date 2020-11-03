package cz.cvut.kbss.termit.validation;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.termit.util.ConfigParam;
import cz.cvut.kbss.termit.util.Configuration;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

/**
 * Validates that a {@link MultilingualString} contains translation in the primary language.
 * <p>
 * Primary language is given by the application's configuration ({@link cz.cvut.kbss.termit.util.ConfigParam#LANGUAGE}).
 */
public class MultilingualStringPrimaryNotBlankValidator
        implements ConstraintValidator<PrimaryNotBlank, MultilingualString> {

    @Autowired
    private Configuration config;

    @Override
    public boolean isValid(MultilingualString multilingualString,
                           ConstraintValidatorContext constraintValidatorContext) {
        if (multilingualString == null) {
            return false;
        }
        return multilingualString.contains(config.get(ConfigParam.LANGUAGE)) &&
                !multilingualString.get(config.get(ConfigParam.LANGUAGE)).trim().isEmpty();
    }
}
