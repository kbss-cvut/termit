package cz.cvut.kbss.termit.validation;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.termit.util.ConfigParam;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.validation.ConstraintValidatorContext;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MultilingualStringPrimaryNotBlankValidatorTest {

    @Mock
    private Configuration config;

    @Mock
    private ConstraintValidatorContext validatorContext;

    @InjectMocks
    private MultilingualStringPrimaryNotBlankValidator sut;

    @Test
    void isValidReturnsFalseForNullValue() {
        assertFalse(sut.isValid(null, validatorContext));
    }

    @Test
    void isValidReturnsFalseWhenValueDoesNotContainPrimaryTranslation() {
        when(config.get(ConfigParam.LANGUAGE)).thenReturn(Constants.DEFAULT_LANGUAGE);
        final MultilingualString value = MultilingualString.create("test", "cs");
        assertFalse(sut.isValid(value, validatorContext));
    }

    @Test
    void isValidReturnsTrueWhenValueContainsPrimaryTranslation() {
        when(config.get(ConfigParam.LANGUAGE)).thenReturn(Constants.DEFAULT_LANGUAGE);
        final MultilingualString value = MultilingualString.create("test", Constants.DEFAULT_LANGUAGE);
        value.set("test");
        assertTrue(sut.isValid(value, validatorContext));
    }
}
