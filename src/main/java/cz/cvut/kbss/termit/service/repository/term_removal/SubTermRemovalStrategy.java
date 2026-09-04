package cz.cvut.kbss.termit.service.repository.term_removal;

import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.service.repository.TermRepositoryService;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;

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
            if (term.getSubTerms() != null) {
                term.getSubTerms().forEach(subTermInfo -> {
                    final Term subTerm = repositoryService.findRequired(subTermInfo.getUri());
                    repositoryService.remove(removalParams.withTerm(subTerm), vocabulary);
                });
            }
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
            final TermInfo termInfo = new TermInfo(term);

            final Collection<Term> subTerms = Optional.ofNullable(term.getSubTerms())
                    .map(children -> children.stream()
                                             .map(HasIdentifier::getUri)
                                             .map(repositoryService::findRequired)
                                             .map(t -> {
                                                 t.consolidateParents();
                                                 return t;
                                             })
                                             .toList())
                    .orElse(Collections.emptyList());

            removeParent(subTerms, termInfo);

            final boolean hasParents = term.getParentTerms() != null && !term.getParentTerms().isEmpty();
            final boolean hasExternalParents = term.getExternalParentTerms() != null && !term.getExternalParentTerms().isEmpty();
            if (hasParents || hasExternalParents) {
                // term has some parents to which children should be reconnected
                reconnectToParents(subTerms, term);
            }

            subTerms.forEach(subTerm -> {
                subTerm.splitExternalAndInternalParents();
                repositoryService.update(subTerm);
            });
        }

        /**
         * Removes the given parent from every given children.
         *
         * @param children children from which the {@code parentToRemove} should be removed
         * @param parentToRemove the parent that should be removed from {@code children}
         */
        private void removeParent(Collection<Term> children, TermInfo parentToRemove) {
            children.forEach(subTerm -> {
                final Collection<TermInfo> childParents = subTerm.getParentTerms();
                if (childParents != null && !childParents.isEmpty()) {
                    childParents.removeIf(parentToRemove::equals);
                }
            });
        }

        /**
         * Adds every parent of {@code term} as parent of every sub-term from {@code subTerms}.
         * Removes the {@code term} from parents of {@code subTerms}.
         *
         * @param subTerms sub-terms whose parents should be modified
         * @param term the parent of {@code subTerms}
         */
        private void reconnectToParents(Collection<Term> subTerms, Term term) {
            final TermInfo oldParentInfo = new TermInfo(term);
            // add every sub-term
            subTerms.forEach(subTerm -> {
                subTerm.getParentTerms().removeIf(oldParentInfo::equals);
                // as a sub-term of each parent
                term.getParentTerms().forEach(parent -> {
                    subTerm.getParentTerms().add(parent);
                });
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
