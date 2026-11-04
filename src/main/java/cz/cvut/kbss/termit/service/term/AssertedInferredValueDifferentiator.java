/*
 * TermIt
 * Copyright (C) 2025 Czech Technical University in Prague
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.service.term;

import cz.cvut.kbss.termit.model.CustomAttribute;
import cz.cvut.kbss.termit.model.Term;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

/**
 * Allows to differentiate asserted and inferred values based on comparison with an original loaded from the repository.
 *
 * <p>This class is required to the pattern where asserted and inferred values are loaded separately from the repository
 * to allow editing of the asserted, yet they are consolidated into one attribute for sending to the client, who need
 * not be aware of this technical distinction.
 *
 * <p>When such consolidated values are then provided back to the server, it is necessary to differentiate them again
 * for the entity to be processable by the persistence provider. This is done by comparing the values with an original,
 * unconsolidated instance, and determining which values from the consolidated input are inferred.
 */
public class AssertedInferredValueDifferentiator {

    /**
     * Differentiates the values of the skos:related attribute.
     *
     * <p>See class documentation for detailed explanation.
     *
     * @param target Input data that require differentiation
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

    private <T> Set<T> differentiateAssertedAndInferred(Set<T> targetAsserted, Set<T> originalInferred) {
        final Set<T> targetInferred = new HashSet<>();
        final Iterator<T> it = targetAsserted.iterator();
        while (it.hasNext()) {
            final T item = it.next();
            if (originalInferred.contains(item)) {
                targetInferred.add(item);
                it.remove();
            }
        }
        return targetInferred;
    }

    /**
     * Differentiates the values of the skos:relatedMatch attribute.
     *
     * <p>See class documentation for detailed explanation.
     *
     * @param target Input data that require differentiation
     * @param original Original instance containing separate asserted and inferred values
     */
    public void differentiateRelatedMatchTerms(Term target, Term original) {
        Objects.requireNonNull(target);
        Objects.requireNonNull(original);
        if (target.getRelatedMatch() == null || original.getInverseRelatedMatch() == null) {
            return;
        }
        target.setInverseRelatedMatch(
                differentiateAssertedAndInferred(target.getRelatedMatch(), original.getInverseRelatedMatch()));
    }

    /**
     * Differentiates the values of the skos:exactMatch attribute.
     *
     * <p>See class documentation for detailed explanation.
     *
     * @param target Input data that require differentiation
     * @param original Original instance containing separate asserted and inferred values
     */
    public void differentiateExactMatchTerms(Term target, Term original) {
        Objects.requireNonNull(target);
        Objects.requireNonNull(original);
        if (target.getExactMatchTerms() == null || original.getInverseExactMatchTerms() == null) {
            return;
        }
        target.setInverseExactMatchTerms(
                differentiateAssertedAndInferred(target.getExactMatchTerms(), original.getInverseExactMatchTerms()));
    }

    /**
     * Differentiates the values of symmetric custom attributes.
     *
     * @param target Input data that require differentiation
     * @param original Original instance containing separate asserted and inferred values
     * @param symmetricCustomAttributes Symmetric custom attributes whose values to differentiate
     */
    public void differentiateSymmetricCustomAttributes(
            Term target, Term original, Collection<CustomAttribute> symmetricCustomAttributes) {
        Objects.requireNonNull(target);
        Objects.requireNonNull(original);
        Objects.requireNonNull(symmetricCustomAttributes);
        if (target.getInverseCustomAttributes() == null) {
            target.setInverseCustomAttributes(new HashMap<>());
        }

        for (CustomAttribute att : symmetricCustomAttributes) {
            final String strUri = att.getUri().toString();
            if (!target.getProperties().containsKey(strUri)
                    || !original.getInverseCustomAttributes().containsKey(strUri)) {
                continue;
            }
            target.getInverseCustomAttributes()
                    .put(
                            strUri,
                            differentiateAssertedAndInferred(
                                    (Set) target.getProperties().get(strUri),
                                    original.getInverseCustomAttributes().get(strUri)));
        }
    }
}
