package cz.cvut.kbss.termit.model.util.validation;

import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.environment.Generator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.validation.ConstraintValidatorContext;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class WithoutQueryParametersValidatorTest {

    @Mock
    private ConstraintValidatorContext ctx;

    private WithoutQueryParametersValidator sut = new WithoutQueryParametersValidator();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    void isValidReturnsTrueForNormalUris() {
        final URI uri = URI.create(SKOS.CONCEPT + Generator.randomInt(0, 1000000));
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
