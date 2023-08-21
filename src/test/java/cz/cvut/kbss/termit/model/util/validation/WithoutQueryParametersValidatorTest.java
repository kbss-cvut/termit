package cz.cvut.kbss.termit.model.util.validation;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.validation.ConstraintValidatorContext;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class WithoutQueryParametersValidatorTest {

    @Mock
    private ConstraintValidatorContext ctx;

    private final WithoutQueryParametersValidator sut = new WithoutQueryParametersValidator();

    @Test
    void isValidReturnsTrueForNormalUris() {
        final URI uri = URI.create(Vocabulary.s_c_term + Generator.randomInt(0, 1000000));
        assertTrue(sut.isValid(uri, ctx));
    }

    @Test
    void isValidReturnsFalseForUriWithQueryParameter() {
        final URI uri = URI.create("https://eur-lex.europa.eu/legal-content/SK/TXT/HTML/?uri=CELEX:32010R0996");
        assertFalse(sut.isValid(uri, ctx));
    }

    @Test
    void isValidReturnsFalseForUriWithMultipleQueryParameters() {
        final URI uri = URI.create("https://eur-lex.europa.eu/legal-content/SK/TXT/HTML/?uri=CELEX:32010R0996&form=sk");
        assertFalse(sut.isValid(uri, ctx));
    }

    @Test
    void isValidReturnsTrueFromNullUri() {
        assertTrue(sut.isValid(null, ctx));
    }
}
