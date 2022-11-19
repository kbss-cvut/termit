package cz.cvut.kbss.termit.service.security;

import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.workspace.EditableVocabularies;
import org.junit.jupiter.api.Disabled;
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
@Disabled("sut is null")
class AuthorizationServiceTest {

    @Mock
    private EditableVocabularies editableVocabularies;

    @InjectMocks
    private AuthorizationService sut;

    private final UserAccount user = Generator.generateUserAccount();

    @Test
    void canEditVocabularyChecksIfVocabularyIsEditableInCurrentWorkspace() {
        user.addType(cz.cvut.kbss.termit.util.Vocabulary.s_c_plny_uzivatel_termitu);
        Environment.setCurrentUser(user);
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        when(editableVocabularies.isEditable(vocabulary)).thenReturn(true);

        assertTrue(sut.canEdit(vocabulary));
        verify(editableVocabularies).isEditable(vocabulary);
    }

    @Test
    void canEditTermChecksIfTermVocabularyIsEditableInCurrentWorkspace() {
        user.addType(cz.cvut.kbss.termit.util.Vocabulary.s_c_plny_uzivatel_termitu);
        Environment.setCurrentUser(user);
        final Term term = Generator.generateTermWithId(Generator.generateUri());
        when(editableVocabularies.isEditable(term.getVocabulary())).thenReturn(true);

        assertTrue(sut.canEdit(term));
        verify(editableVocabularies).isEditable(term.getVocabulary());
    }

    @Test
    void canEditVocabularyReturnsFalseWhenCurrentUserIsReader() {
        user.addType(cz.cvut.kbss.termit.util.Vocabulary.s_c_omezeny_uzivatel_termitu);
        Environment.setCurrentUser(user);
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();

        assertFalse(sut.canEdit(vocabulary));
    }

    @Test
    void canEditVocabularyReturnsTrueWhenCurrentUserIsEditor() {
        user.addType(cz.cvut.kbss.termit.util.Vocabulary.s_c_plny_uzivatel_termitu);
        Environment.setCurrentUser(user);
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        when(editableVocabularies.isEditable(any(Vocabulary.class))).thenReturn(true);

        assertTrue(sut.canEdit(vocabulary));
    }

    @Test
    void canEditTermReturnsFalseWhenCurrentUserIsReader() {
        user.addType(cz.cvut.kbss.termit.util.Vocabulary.s_c_omezeny_uzivatel_termitu);
        Environment.setCurrentUser(user);
        final Term term = Generator.generateTermWithId(Generator.generateUri());

        assertFalse(sut.canEdit(term));
    }

    @Test
    void canEditTermReturnsTrueWhenCurrentUserIsAdmin() {
        user.addType(cz.cvut.kbss.termit.util.Vocabulary.s_c_administrator_termitu);
        Environment.setCurrentUser(user);
        final Term term = Generator.generateTermWithId(Generator.generateUri());
        when(editableVocabularies.isEditable(term.getVocabulary())).thenReturn(true);

        assertTrue(sut.canEdit(term));
    }
}
