package cz.cvut.kbss.termit.service.repository.term_removal;

import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;

/**
 * Describes how should be a term removed
 * @param termToRemove The Term that will be removed
 * @param subTermsStrategy Strategy to use for sub-terms handling
 * @param removeOccurrences Whether {@link cz.cvut.kbss.termit.model.assignment.TermOccurrence term occurrences} should be removed.
 *                          When {@code false} and a term occurrence exists, the removal will fail
 *                          and the term will not be removed.
 * @param removeRelationships Whether relationships referencing the term should be removed.
 *                            When {@code false} and the term is referenced in some relationship, the removal will fail
 *                            and the term will not be removed.
 * @see cz.cvut.kbss.termit.service.repository.TermRepositoryService#remove(TermRemovalParams, Vocabulary)
 */
public record TermRemovalParams(
        Term termToRemove,
        SubTermRemovalStrategy subTermsStrategy,
        boolean removeOccurrences,
        boolean removeRelationships
) {
    /**
     * Creates a copy of this params replacing the term to remove.
     *
     * @param term the new term to remove
     * @return copy of the params with replaced term instance
     */
    public TermRemovalParams withTerm(Term term) {
        return new TermRemovalParams(term, subTermsStrategy, removeOccurrences, removeRelationships);
    }
}
