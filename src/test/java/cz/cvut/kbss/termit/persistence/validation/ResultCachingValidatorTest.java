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
package cz.cvut.kbss.termit.persistence.validation;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.event.VocabularyContentModified;
import cz.cvut.kbss.termit.model.validation.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResultCachingValidatorTest {

    @Mock
    private Validator validator;

    private ResultCachingValidator sut;

    @BeforeEach
    void setUp() {
        this.sut = spy(new ResultCachingValidator());
        when(sut.getValidator()).thenReturn(validator);
    }

    @Test
    void invokesInternalValidatorWhenNoResultsAreCached() {
        final List<ValidationResult> results = Collections.singletonList(new ValidationResult());
        when(validator.validate(anyCollection())).thenReturn(results);
        final Set<URI> vocabularies = Collections.singleton(Generator.generateUri());
        final List<ValidationResult> result = sut.validate(vocabularies);
        assertEquals(results, result);
        verify(validator).validate(vocabularies);
    }

    @Test
    void returnsCachedResultsWhenArgumentsMatch() {
        final List<ValidationResult> results = Collections.singletonList(new ValidationResult());
        when(validator.validate(anyCollection())).thenReturn(results);
        final Set<URI> vocabularies = Collections.singleton(Generator.generateUri());
        final List<ValidationResult> resultOne = sut.validate(vocabularies);
        final List<ValidationResult> resultTwo = sut.validate(vocabularies);
        assertEquals(resultOne, resultTwo);
        verify(validator).validate(vocabularies);
    }

    @Test
    void evictCacheClearsCachedValidationResults() {
        final List<ValidationResult> results = Collections.singletonList(new ValidationResult());
        when(validator.validate(anyCollection())).thenReturn(results);
        final Set<URI> vocabularies = Collections.singleton(Generator.generateUri());
        final List<ValidationResult> resultOne = sut.validate(vocabularies);
        sut.evictCache(new VocabularyContentModified(this, null));
        final List<ValidationResult> resultTwo = sut.validate(vocabularies);
        verify(validator, times(2)).validate(vocabularies);
        assertNotSame(resultOne, resultTwo);
    }
}
