package cz.cvut.kbss.termit.workspace;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.util.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EditableVocabulariesTest {

    private Configuration configuration;

    private EditableVocabularies sut;

    @BeforeEach
    void setUp() {
        this.configuration = new Configuration();
        this.sut = new EditableVocabularies(configuration);
    }

    @Test
    void isEditableReturnsTrueForVocabularyRegisteredAsEditable() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final URI context = Generator.generateUri();
        sut.registerEditableVocabulary(vocabulary.getUri(), context);

        assertTrue(sut.isEditable(vocabulary));
    }

    @Test
    void isEditableReturnsTrueForUnregisteredVocabularyWhenAllVocabulariesAreEditable() {
        configuration.getWorkspace().setAllVocabulariesEditable(true);
        this.sut = new EditableVocabularies(configuration);
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();

        assertTrue(sut.isEditable(vocabulary));
    }

    @Test
    void isEditableReturnsFalseForUnregisteredVocabularyWhenAllVocabulariesAreNotEditable() {
        configuration.getWorkspace().setAllVocabulariesEditable(false);
        this.sut = new EditableVocabularies(configuration);
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();

        assertFalse(sut.isEditable(vocabulary));
    }

    @Test
    void clearRemovesPreviouslyRegisteredContexts() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final URI context = Generator.generateUri();
        sut.registerEditableVocabulary(vocabulary.getUri(), context);

        assertEquals(Set.of(context), sut.getRegisteredContexts());
        sut.clear();
        assertTrue(sut.getRegisteredContexts().isEmpty());
    }
}
