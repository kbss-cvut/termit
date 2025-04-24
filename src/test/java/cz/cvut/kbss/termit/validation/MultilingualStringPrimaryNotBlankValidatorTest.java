/*
 * TermIt
 * Copyright (C) 2025 Czech Technical University in Prague
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
package cz.cvut.kbss.termit.validation;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.termit.util.Configuration;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
