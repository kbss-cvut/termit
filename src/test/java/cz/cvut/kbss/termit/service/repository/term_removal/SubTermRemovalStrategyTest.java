package cz.cvut.kbss.termit.service.repository.term_removal;

import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.TermItException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.service.repository.TermRepositoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubTermRemovalStrategyTest {

    @Mock
    private TermRepositoryService repositoryService;

    private Vocabulary vocabulary;

    private Term term;

    @BeforeEach
    void setUp() {
        vocabulary = Generator.generateVocabularyWithId();
        term = Generator.generateTermWithId(vocabulary.getUri());
        vocabulary.addRootTerm(term);
        term.setSubTerms(new HashSet<>());
        term.setParentTerms(new HashSet<>());
        term.setExternalParentTerms(new HashSet<>());

        lenient().when(repositoryService.findRequired(term.getUri())).thenReturn(term);

        lenient().doAnswer(answer -> {
            Term t = answer.getArgument(0,Term.class);
            Vocabulary voc = answer.getArgument(1, Vocabulary.class);
            t.setVocabulary(voc.getUri());
            voc.addRootTerm(t);
            return null;
        }).when(repositoryService).addRootTermToVocabulary(any(), any());
    }

    private TermRemovalParams withStrategy(SubTermRemovalStrategy strategy) {
        return new TermRemovalParams(term, strategy, false, false);
    }

    @Test
    void failThrowsWhenTermHasChildren() {
        final TermRemovalParams params = withStrategy(SubTermRemovalStrategy.FAIL);
        final TermInfo child = Generator.generateTermInfoWithId();
        term.setSubTerms(Set.of(child));

        final TermItException exception = assertThrows(TermItException.class, () -> params.subTermsStrategy().apply(params, vocabulary, repositoryService));
        assertEquals("error.term.remove.hasSubTerms", exception.getMessageId());
    }

    @Test
    void failDoesNotThrowWhenTermHasNoChildren() {
        final TermRemovalParams params = withStrategy(SubTermRemovalStrategy.FAIL);
        assertDoesNotThrow(() -> params.subTermsStrategy().apply(params, vocabulary, repositoryService));
    }

    @Test
    void cascadeRemovesAllChildrenUsingSameRemovalParameters() {
        final TermRemovalParams params = withStrategy(SubTermRemovalStrategy.CASCADE);
        final TermInfo firstChildInfo = Generator.generateTermInfoWithId();
        final TermInfo secondChildInfo = Generator.generateTermInfoWithId();

        final Term firstChild = Generator.generateTermWithId(vocabulary.getUri());
        firstChild.setUri(firstChildInfo.getUri());

        final Term secondChild = Generator.generateTermWithId(vocabulary.getUri());
        secondChild.setUri(secondChildInfo.getUri());

        term.setSubTerms(Set.of(firstChildInfo, secondChildInfo));

        when(repositoryService.findRequired(firstChildInfo.getUri())).thenReturn(firstChild);
        when(repositoryService.findRequired(secondChildInfo.getUri())).thenReturn(secondChild);

        params.subTermsStrategy().apply(params, vocabulary, repositoryService);

        verify(repositoryService).findRequired(firstChildInfo.getUri());
        verify(repositoryService).findRequired(secondChildInfo.getUri());
        verify(repositoryService).remove(params.withTerm(firstChild), vocabulary);
        verify(repositoryService).remove(params.withTerm(secondChild), vocabulary);
        verifyNoMoreInteractions(repositoryService);
    }

    @Test
    void reconnectUpdatesChildrenToHandleVocabularyRootTermAddition() {
        final TermRemovalParams params = withStrategy(SubTermRemovalStrategy.RECONNECT);
        final TermInfo firstChild = Generator.generateTermInfoWithId();
        final TermInfo secondChild = Generator.generateTermInfoWithId();

        final Term firstChildTerm = firstChild.toTerm();
        final Term secondChildTerm = secondChild.toTerm();

        makeParent(firstChildTerm, term);
        makeParent(secondChildTerm, term);

        firstChild.setVocabulary(vocabulary.getUri());
        secondChild.setVocabulary(Generator.generateUri());

        when(repositoryService.findRequired(firstChild.getUri())).thenReturn(firstChildTerm);
        when(repositoryService.findRequired(secondChild.getUri())).thenReturn(secondChildTerm);

        params.subTermsStrategy().apply(params, vocabulary, repositoryService);

        verify(repositoryService, atLeastOnce()).findRequired(any());
        // repository post-update handles root term assignment
        verify(repositoryService).update(firstChildTerm);
        verify(repositoryService).update(secondChildTerm);
        verifyNoMoreInteractions(repositoryService);
    }

    @Test
    void reconnectLinksChildrenToAllParents() {
        final TermRemovalParams params = withStrategy(SubTermRemovalStrategy.RECONNECT);

        final Term child = Generator.generateTermWithId(vocabulary.getUri());
        final Term parent = Generator.generateTermWithId(vocabulary.getUri());
        final Vocabulary externalVocabulary = Generator.generateVocabularyWithId();
        final Term externalParent = Generator.generateTermWithId(externalVocabulary.getUri());

        makeParent(child, term);
        makeParent(term, parent);
        makeParent(term, externalParent);

        vocabulary.getRootTerms().clear();
        vocabulary.addRootTerm(parent);

        term.setExternalParentTerms(Set.of(externalParent.toTermInfo()));

        when(repositoryService.findRequired(child.getUri())).thenReturn(child);
        params.subTermsStrategy().apply(params, vocabulary, repositoryService);

        assertEquals(Set.of(parent.toTermInfo()), child.getParentTerms(), "Child must be assigned to the parent of the removed term");
        assertEquals(Set.of(externalParent.toTermInfo()), child.getExternalParentTerms());

        assertEquals(Set.of(parent.getUri()), vocabulary.getRootTerms(), "Vocabulary root terms must remain unchanged");

        verify(repositoryService).update(child);
        verifyNoMoreInteractions(repositoryService);
    }

    private void makeParent(Term childTerm, Term parentTerm) {
        if (childTerm.getParentTerms() == null) {
            childTerm.setParentTerms(new HashSet<>());
        }
        if (parentTerm.getSubTerms() == null) {
            parentTerm.setSubTerms(new HashSet<>());
        }

        childTerm.getParentTerms().add(new TermInfo(parentTerm));
        parentTerm.getSubTerms().add(new TermInfo(childTerm));
    }
}
