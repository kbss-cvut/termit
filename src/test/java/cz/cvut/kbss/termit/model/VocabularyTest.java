package cz.cvut.kbss.termit.model;

import cz.cvut.kbss.termit.environment.Generator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VocabularyTest {

    @Test
    void isSnapshotReturnsTrueWhenInstanceHasSnapshotType() {
        final Vocabulary original = Generator.generateVocabularyWithId();
        final Vocabulary snapshot = Generator.generateVocabularyWithId();
        snapshot.addType(cz.cvut.kbss.termit.util.Vocabulary.s_c_verze_slovniku);
        assertFalse(original.isSnapshot());
        assertTrue(snapshot.isSnapshot());
    }
}
