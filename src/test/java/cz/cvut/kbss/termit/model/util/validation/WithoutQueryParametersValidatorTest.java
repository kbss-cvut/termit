/*
 * TermIt
 * Copyright (C) 2023 Czech Technical University in Prague
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
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
