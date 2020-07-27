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
