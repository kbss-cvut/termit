package cz.cvut.kbss.termit.service.security.authorization;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    void canCreateInChecksIfUserCanModifyTargetVocabulary() {
        final Vocabulary target = Generator.generateVocabularyWithId();
        when(vocabularyAuthorizationService.canModify(target)).thenReturn(true);

        assertTrue(sut.canCreateIn(target));
        verify(vocabularyAuthorizationService).canModify(target);
    }

    @Test
    void canCreateChildChecksIfTermCanBeCreatedInParentTermVocabulary() {
        final Vocabulary target = Generator.generateVocabularyWithId();
        final Term parent = Generator.generateTermWithId(target.getUri());
        when(vocabularyAuthorizationService.canModify(target)).thenReturn(true);

        assertTrue(sut.canCreateChild(parent));
        verify(vocabularyAuthorizationService).canModify(target);
    }

    @Test
    void canReadChecksIfUserCanReadTermVocabulary() {
        when(vocabularyAuthorizationService.canRead(any(Vocabulary.class))).thenReturn(true);
        final Term term = Generator.generateTermWithId();
        final Vocabulary v = Generator.generateVocabularyWithId();
        term.setVocabulary(v.getUri());

        assertTrue(sut.canRead(term));
        verify(vocabularyAuthorizationService).canRead(v);
    }

    @Test
    void canModifyTermChecksIfUserCanModifyTermVocabulary() {
        final Term term = Generator.generateTermWithId(Generator.generateUri());
        when(vocabularyAuthorizationService.canModify(any(Vocabulary.class))).thenReturn(true);

        assertTrue(sut.canModify(term));
        verify(vocabularyAuthorizationService).canModify(new Vocabulary(term.getVocabulary()));
    }

    @Test
    void canRemoveChecksIfUserCanRemoveTermVocabulary() {
        when(vocabularyAuthorizationService.canRemove(any(Vocabulary.class))).thenReturn(true);
        final Term term = Generator.generateTermWithId();
        final Vocabulary v = Generator.generateVocabularyWithId();
        term.setVocabulary(v.getUri());

        assertTrue(sut.canRemove(term));
        verify(vocabularyAuthorizationService).canRemove(v);
    }
}
