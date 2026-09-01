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
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.assignment.TermDefinitionSource;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DisjointReferencesValidatorTest {

    @Mock
    private Disjoint annotationInstance;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ConstraintValidatorContext validatorContext;

    @InjectMocks
    private DisjointReferencesValidator sut;

    @BeforeEach
    void setUp() throws Exception {
        MultilingualStringPrimaryNotBlankValidatorTest.resetStaticReflectionCache();
        when(annotationInstance.value()).thenReturn(new String[]{"parentTerms", "related"});
        sut.initialize(annotationInstance);
    }

    @Test
    void isValidReturnsTrueForNullBean() {
        assertTrue(sut.isValid(null, validatorContext));
        verifyNoInteractions(validatorContext);
    }

    @Test
    void isValidReturnsTrueWhenParentTermsAndRelatedAreDisjoint() {
        final Term term = Generator.generateTermWithId();
        term.setParentTerms(Set.of(Generator.generateTermInfoWithId(), Generator.generateTermInfoWithId()));
        term.setRelated(Set.of(Generator.generateTermInfoWithId()));

        assertTrue(sut.isValid(term, validatorContext));
        verifyNoInteractions(validatorContext);
    }

    @Test
    void isValidReturnsFalseAndReportsViolationOnLaterFieldWhenParentTermsAndRelatedOverlap() {
        final Term term = Generator.generateTermWithId();
        final TermInfo shared = Generator.generateTermInfoWithId();
        term.setParentTerms(Set.of(shared));
        term.setRelated(new HashSet<>(Set.of(shared, Generator.generateTermInfoWithId())));

        assertFalse(sut.isValid(term, validatorContext));
        verify(validatorContext).disableDefaultConstraintViolation();
        verify(validatorContext.buildConstraintViolationWithTemplate(any()))
                .addPropertyNode("related");
    }

    @Test
    void isValidReturnsTrueWhenAllValidatedFieldsAreNull() {
        assertTrue(sut.isValid(Generator.generateTermWithId(), validatorContext));
        verifyNoInteractions(validatorContext);
    }

    @Test
    void isValidIgnoresElementsWithNullUriWithinSetField() {
        final Term term = Generator.generateTermWithId();
        term.setParentTerms(new HashSet<>(Set.of(new TermInfo())));
        term.setRelated(new HashSet<>(Set.of(new TermInfo())));

        assertTrue(sut.isValid(term, validatorContext));
    }

    @Test
    void isValidVerifiesConfiguredDisjointPairs() {
        final Term term = Generator.generateTermWithId();
        final TermInfo shared = Generator.generateTermInfoWithId();
        term.setParentTerms(Set.of(shared));
        term.setRelatedMatch(Set.of(shared));

        reset(annotationInstance);
        when(annotationInstance.value()).thenReturn(new String[]{"parentTerms", "relatedMatch"});
        sut.initialize(annotationInstance);

        assertFalse(sut.isValid(term, validatorContext));
    }

    @Test
    void isValidReturnsFalseForSingularHasIdentifierFieldsSharingSameUri() {
        // Configure the validator to inspect the same singular HasIdentifier field twice,
        // exercising the singular-value branch of the extractor.
        reset(annotationInstance);
        when(annotationInstance.value()).thenReturn(new String[]{"definitionSource", "definitionSource"});
        sut.initialize(annotationInstance);

        final Term term = Generator.generateTermWithId();
        final TermDefinitionSource src = new TermDefinitionSource();
        src.setUri(Generator.generateUri());
        term.setDefinitionSource(src);

        assertFalse(sut.isValid(term, validatorContext));
        verify(validatorContext).disableDefaultConstraintViolation();
    }

    @Test
    void isValidThrowsIllegalArgumentExceptionForUnsupportedFieldType() {
        reset(annotationInstance);
        when(annotationInstance.value()).thenReturn(new String[]{"description"});
        sut.initialize(annotationInstance);

        final Term term = Generator.generateTermWithId();
        term.setDescription(MultilingualString.create("desc", Environment.LANGUAGE));
        assertThrows(IllegalArgumentException.class, () -> sut.isValid(term, validatorContext));
    }

    @Test
    void isValidThrowsIllegalArgumentExceptionWhenFieldDoesNotExist() {
        reset(annotationInstance);
        when(annotationInstance.value()).thenReturn(new String[]{"nonExistent"});
        sut.initialize(annotationInstance);

        assertThrows(IllegalArgumentException.class,
                     () -> sut.isValid(Generator.generateTermWithId(), validatorContext));
    }

    @Test
    void isValidThrowsIllegalArgumentExceptionWhenAnnotationMissingOnClass() {
        assertThrows(IllegalArgumentException.class, () -> sut.isValid(new TermInfo(), validatorContext));
    }
}
