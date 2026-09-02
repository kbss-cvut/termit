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
    void reconnectMakesChildrenRootTermsWhenRemovedTermHasNoParentsInSameVocabulary() {
        final TermRemovalParams params = withStrategy(SubTermRemovalStrategy.RECONNECT);
        final TermInfo firstChild = Generator.generateTermInfoWithId();
        final TermInfo secondChild = Generator.generateTermInfoWithId();

        term.setSubTerms(Set.of(firstChild, secondChild));

        params.subTermsStrategy().apply(params, vocabulary, repositoryService);

        assertEquals(Set.of(term.getUri(), firstChild.getUri(), secondChild.getUri()), vocabulary.getRootTerms());
        verifyNoMoreInteractions(repositoryService);
    }

    @Test
    void reconnectLinksChildrenToAllParents() {
        final TermRemovalParams params = withStrategy(SubTermRemovalStrategy.RECONNECT);

        final TermInfo child = Generator.generateTermInfoWithId();
        final Term parent = Generator.generateTermWithId(vocabulary.getUri());
        final Vocabulary externalVocabulary = Generator.generateVocabularyWithId();
        final Term externalParent = Generator.generateTermWithId(externalVocabulary.getUri());

        parent.setSubTerms(new HashSet<>());
        externalParent.setSubTerms(new HashSet<>());

        term.setSubTerms(Set.of(child));
        term.setParentTerms(Set.of(parent.toTermInfo()));
        term.setExternalParentTerms(Set.of(externalParent.toTermInfo()));

        when(repositoryService.findRequired(parent.getUri())).thenReturn(parent);
        when(repositoryService.findRequired(externalParent.getUri())).thenReturn(externalParent);

        params.subTermsStrategy().apply(params, vocabulary, repositoryService);

        assertEquals(Set.of(child), parent.getSubTerms());
        assertEquals(Set.of(child), externalParent.getSubTerms());

        assertEquals(Set.of(term.getUri()), vocabulary.getRootTerms(), "Children must not be added as root terms");

        verify(repositoryService).findRequired(parent.getUri());
        verify(repositoryService).findRequired(externalParent.getUri());
        verifyNoMoreInteractions(repositoryService);
    }
}
