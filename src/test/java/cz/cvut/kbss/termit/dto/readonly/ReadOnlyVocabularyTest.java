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
package cz.cvut.kbss.termit.dto.readonly;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Vocabulary;
import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReadOnlyVocabularyTest {

    @Test
    void constructorCopiesAllRelevantAttributesFromSpecifiedVocabulary() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();

        final ReadOnlyVocabulary result = new ReadOnlyVocabulary(vocabulary);
        assertEquals(vocabulary.getUri(), result.getUri());
        assertEquals(vocabulary.getLabel(), result.getLabel());
        assertEquals(vocabulary.getDescription(), result.getDescription());
    }

    @Test
    void constructorCopiesImportedVocabulariesFromSpecifiedVocabularyWhenTheyAreAvailable() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        vocabulary.setImportedVocabularies(
                IntStream.range(0, 3).mapToObj(i -> Generator.generateUri()).collect(Collectors.toSet()));

        final ReadOnlyVocabulary result = new ReadOnlyVocabulary(vocabulary);
        assertEquals(vocabulary.getImportedVocabularies(), result.getImportedVocabularies());
    }
}
