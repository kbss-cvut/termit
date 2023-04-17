package cz.cvut.kbss.termit.service.security.authorization;

import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.workspace.EditableVocabularies;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VocabularyAuthorizationServiceTest {

    @Mock
    private EditableVocabularies editableVocabularies;

    @InjectMocks
    private VocabularyAuthorizationService sut;

    private final UserAccount user = Generator.generateUserAccount();

    @Test
    void canEditVocabularyChecksIfVocabularyIsEditableInCurrentWorkspace() {
        user.addType(cz.cvut.kbss.termit.util.Vocabulary.s_c_plny_uzivatel_termitu);
        Environment.setCurrentUser(user);
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        when(editableVocabularies.isEditable(vocabulary)).thenReturn(true);

        assertTrue(sut.canModify(vocabulary));
        verify(editableVocabularies).isEditable(vocabulary);
    }

    @Test
    void canEditVocabularyReturnsFalseWhenCurrentUserIsReader() {
        user.addType(cz.cvut.kbss.termit.util.Vocabulary.s_c_omezeny_uzivatel_termitu);
        Environment.setCurrentUser(user);
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();

        assertFalse(sut.canModify(vocabulary));
    }

    @Test
    void canEditVocabularyReturnsTrueWhenCurrentUserIsEditor() {
        user.addType(cz.cvut.kbss.termit.util.Vocabulary.s_c_plny_uzivatel_termitu);
        Environment.setCurrentUser(user);
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        when(editableVocabularies.isEditable(any(Vocabulary.class))).thenReturn(true);

        assertTrue(sut.canModify(vocabulary));
    }
}
