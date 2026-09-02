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
}
