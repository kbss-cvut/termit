package cz.cvut.kbss.termit.validation;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.termit.util.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.validation.ConstraintValidatorContext;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MultilingualStringPrimaryNotBlankValidatorTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
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
        when(config.getPersistence().getLanguage()).thenReturn("es");
        final MultilingualString value = MultilingualString.create("test", "cs");
        assertFalse(sut.isValid(value, validatorContext));
    }

    @Test
    void isValidReturnsTrueWhenValueContainsPrimaryTranslation() {
        when(config.getPersistence().getLanguage()).thenReturn("es");
        final MultilingualString value = MultilingualString.create("test", "es");
        value.set("test");
        assertTrue(sut.isValid(value, validatorContext));
    }
}
