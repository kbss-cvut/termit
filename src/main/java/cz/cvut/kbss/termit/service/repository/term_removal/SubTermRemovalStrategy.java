package cz.cvut.kbss.termit.service.repository.term_removal;

import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.service.repository.TermRepositoryService;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Describes an operation how should be sub-terms handled during term removal
 */
public enum SubTermRemovalStrategy implements TermParamsApplier {
    /**
     * All sub-terms will be removed using the same {@link TermRemovalParams}
     */
    CASCADE(CascadeRemoveApplier.INSTANCE),

    /**
     * Sub-terms will be moved one level up, becoming children of the parent(s) of the term being removed.
     * When the term being removed has no parents in the current vocabulary,
     * the sub-terms will become root terms of the vocabulary.
     */
    RECONNECT(ReconnectApplier.INSTANCE),

    /**
     * If the term has sub-terms, the removal will fail
     */
    FAIL(FailApplier.INSTANCE);

    private final TermParamsApplier applier;

    SubTermRemovalStrategy(TermParamsApplier applier) {
        this.applier = Objects.requireNonNull(applier);
    }

    @Override
    public void apply(TermRemovalParams removalParams, Vocabulary vocabulary, TermRepositoryService repositoryService) {
        applier.apply(removalParams, vocabulary, repositoryService);
    }

    /**
     * Removes every child of the given term
     */
    private static class CascadeRemoveApplier implements TermParamsApplier {
        static final CascadeRemoveApplier INSTANCE = new CascadeRemoveApplier();

        @Override
        public void apply(TermRemovalParams removalParams, Vocabulary vocabulary,
                          TermRepositoryService repositoryService) {
            final Term term = removalParams.termToRemove();
            term.getSubTerms().forEach(subTermInfo -> {
                final Term subTerm = repositoryService.findRequired(subTermInfo.getUri());
                repositoryService.remove(removalParams.withTerm(subTerm), vocabulary);
            });
        }
    }

    /**
     * Moves every subterm one level up.
     * Every child becomes child of its grandparent.
     * If the term being removed has no parents in the current vocabulary, children become root terms.
     */
    private static class ReconnectApplier implements TermParamsApplier {
        static final ReconnectApplier INSTANCE = new ReconnectApplier();

        @Override
        public void apply(TermRemovalParams removalParams, Vocabulary vocabulary,
                          TermRepositoryService repositoryService) {
            final Term term = removalParams.termToRemove();
            if (!term.hasParentInSameVocabulary()) {
                // term has no parents in the same vocabulary
                makeRoots(term, vocabulary);
            }
            if (!term.getParentTerms().isEmpty() || !term.getExternalParentTerms().isEmpty()) {
                // term has some parents to which children should be reconnected
                reconnectToParents(term, repositoryService);
            }
        }

        private void makeRoots(Term term, Vocabulary vocabulary) {
            term.getSubTerms().forEach(subTermInfo -> {
                vocabulary.getRootTerms().add(subTermInfo.getUri());
            });
        }

        private void reconnectToParents(Term term, TermRepositoryService repositoryService) {
            // local and external parents
            final Set<Term> parents = Stream.of(term.getParentTerms(), term.getExternalParentTerms())
                                      .flatMap(Collection::stream)
                                      .map(HasIdentifier::getUri)
                                      .distinct() // in case of consolidated parents
                                      .map(repositoryService::findRequired)
                                      .collect(Collectors.toSet());
            // add every sub-term
            term.getSubTerms().forEach(subTermInfo -> {
                // as a sub-term of each parent
                parents.forEach(parent -> parent.getSubTerms().add(subTermInfo));
            });
        }
    }

    /**
     * Throws an exception when the term to be removed has sub-terms.
     */
    private static class FailApplier implements TermParamsApplier {
        static final FailApplier INSTANCE = new FailApplier();
        @Override
        public void apply(TermRemovalParams removalParams, Vocabulary vocabulary,
                          TermRepositoryService repositoryService) {
            final Term term = removalParams.termToRemove();
            if (!term.getSubTerms().isEmpty()) {
                throw TermRepositoryService.hasSubTermsException(term.getSubTerms());
            }
        }
    }

}
