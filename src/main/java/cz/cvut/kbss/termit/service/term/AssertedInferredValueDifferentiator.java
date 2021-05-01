package cz.cvut.kbss.termit.service.term;

import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.model.Term;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

/**
 * Allows to differentiate asserted and inferred values based on comparison with an original loaded from the
 * repository.
 * <p>
 * This class is required to the pattern where asserted and inferred values are loaded separately from the repository to
 * allow editing of the asserted, yet they are consolidated into one attribute for sending to the client, who need not
 * be aware of this technical distinction.
 * <p>
 * When such consolidated values are then provided back to the server, it is necessary to differentiate them again for
 * the entity to be processable by the persistence provider. This is done by comparing the values with an original,
 * unconsolidated instance, and determining which values from the consolidated input are inferred.
 */
public class AssertedInferredValueDifferentiator {

    /**
     * Differentiates the values of the skos:related attribute.
     * <p>
     * See class documentation for detailed explanation.
     *
     * @param target   Input data that require differentiation
     * @param original Original instance containing separate asserted and inferred values
     */
    public void differentiateRelatedTerms(Term target, Term original) {
        Objects.requireNonNull(target);
        Objects.requireNonNull(original);
        if (target.getRelated() == null || original.getInverseRelated() == null) {
            return;
        }
        target.setInverseRelated(differentiateAssertedAndInferred(target.getRelated(), original.getInverseRelated()));
    }

    private Set<TermInfo> differentiateAssertedAndInferred(Set<TermInfo> targetAsserted, Set<TermInfo> originalInferred) {
        final Set<TermInfo> targetInferred = new HashSet<>();
        final Iterator<TermInfo> it = targetAsserted.iterator();
        while (it.hasNext()) {
            final TermInfo item = it.next();
            if (originalInferred.contains(item)) {
                targetInferred.add(item);
                it.remove();
            }
        }
        return targetInferred;
    }

    /**
     * Differentiates the values of the skos:relatedMatch attribute.
     * <p>
     * See class documentation for detailed explanation.
     *
     * @param target   Input data that require differentiation
     * @param original Original instance containing separate asserted and inferred values
     */
    public void differentiateRelatedMatchTerms(Term target, Term original) {
        Objects.requireNonNull(target);
        Objects.requireNonNull(original);
        if (target.getRelatedMatch() == null || original.getInverseRelatedMatch() == null) {
            return;
        }
        target.setInverseRelatedMatch(differentiateAssertedAndInferred(target.getRelatedMatch(), original.getInverseRelatedMatch()));
    }

    /**
     * Differentiates the values of the skos:exactMatch attribute.
     * <p>
     * See class documentation for detailed explanation.
     *
     * @param target   Input data that require differentiation
     * @param original Original instance containing separate asserted and inferred values
     */
    public void differentiateExactMatchTerms(Term target, Term original) {
        Objects.requireNonNull(target);
        Objects.requireNonNull(original);
        if (target.getExactMatchTerms() == null || original.getInverseExactMatchTerms() == null) {
            return;
        }
        target.setInverseExactMatchTerms(differentiateAssertedAndInferred(target.getExactMatchTerms(), original.getInverseExactMatchTerms()));
    }
}
