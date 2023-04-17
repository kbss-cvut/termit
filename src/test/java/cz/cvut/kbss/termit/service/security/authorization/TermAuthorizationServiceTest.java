package cz.cvut.kbss.termit.service.security.authorization;

import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.model.Vocabulary;
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
class TermAuthorizationServiceTest {

    @Mock
    private VocabularyAuthorizationService vocabularyAuthorizationService;

    @InjectMocks
    private TermAuthorizationService sut;

    @Test
    void canModifyTermChecksIfTermVocabularyIsEditableInCurrentWorkspace() {
        final Term term = Generator.generateTermWithId(Generator.generateUri());
        when(vocabularyAuthorizationService.canModify(any(Vocabulary.class))).thenReturn(true);

        assertTrue(sut.canModify(term));
        verify(vocabularyAuthorizationService).canModify(new Vocabulary(term.getVocabulary()));
    }

    @Test
    void canCreateInChecksIfUserCanModifyTargetVocabulary() {
        final UserAccount current = Generator.generateUserAccount();
        current.addType(cz.cvut.kbss.termit.util.Vocabulary.s_c_plny_uzivatel_termitu);
        Environment.setCurrentUser(current);
        final Vocabulary target = Generator.generateVocabularyWithId();
        when(vocabularyAuthorizationService.canModify(target)).thenReturn(true);

        assertTrue(sut.canCreateIn(target));
        verify(vocabularyAuthorizationService).canModify(target);
    }

    @Test
    void canCreateInReturnsFalseWhenCurrentUserIsNotAtLeastEditor() {
        final UserAccount current = Generator.generateUserAccount();
        current.addType(cz.cvut.kbss.termit.util.Vocabulary.s_c_omezeny_uzivatel_termitu);
        Environment.setCurrentUser(current);

        assertFalse(sut.canCreateIn(Generator.generateVocabularyWithId()));
    }
}
