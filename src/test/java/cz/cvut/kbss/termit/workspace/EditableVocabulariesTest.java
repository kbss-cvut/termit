package cz.cvut.kbss.termit.workspace;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.util.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class EditableVocabulariesTest {

    private final Configuration.Workspace workspaceConfig = new Configuration.Workspace();

    private EditableVocabularies sut;

    @BeforeEach
    void setUp() {
        final Configuration configuration = new Configuration();
        configuration.setWorkspace(workspaceConfig);
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
        workspaceConfig.setAllVocabulariesEditable(true);
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();

        assertTrue(sut.isEditable(vocabulary));
    }

    @Test
    void isEditableReturnsFalseForUnregisteredVocabularyWhenAllVocabulariesAreNotEditable() {
        workspaceConfig.setAllVocabulariesEditable(false);
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();

        assertFalse(sut.isEditable(vocabulary));
    }
}
