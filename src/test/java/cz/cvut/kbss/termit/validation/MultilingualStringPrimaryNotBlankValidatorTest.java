package cz.cvut.kbss.termit.validation;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.termit.util.ConfigParam;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.validation.ConstraintValidatorContext;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class MultilingualStringPrimaryNotBlankValidatorTest {

    @Mock
    private Configuration config;

    @Mock
    private ConstraintValidatorContext validatorContext;

    @InjectMocks
    private MultilingualStringPrimaryNotBlankValidator sut;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        when(config.get(ConfigParam.LANGUAGE)).thenReturn(Constants.DEFAULT_LANGUAGE);
    }

    @Test
    void isValidReturnsFalseForNullValue() {
        assertFalse(sut.isValid(null, validatorContext));
    }

    @Test
    void isValidReturnsFalseWhenValueDoesNotContainPrimaryTranslation() {
        final MultilingualString value = MultilingualString.create("test", "cs");
        assertFalse(sut.isValid(value, validatorContext));
    }

    @Test
    void isValidReturnsTrueWhenValueContainsPrimaryTranslation() {
        final MultilingualString value = MultilingualString.create("test", Constants.DEFAULT_LANGUAGE);
        value.set("test");
        assertTrue(sut.isValid(value, validatorContext));
    }
}
