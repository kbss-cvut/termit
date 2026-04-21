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
package cz.cvut.kbss.termit.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import cz.cvut.kbss.jopa.model.annotations.CascadeType;
import cz.cvut.kbss.jopa.model.annotations.FetchType;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.model.changetracking.Audited;
import cz.cvut.kbss.termit.model.util.SupportsSnapshots;

import java.net.URI;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@Audited
@OWLClass(iri = SKOS.CONCEPT)
public class Term extends AbstractFullTerm implements SupportsSnapshots {

    /**
     * Parent terms from the same vocabulary.
     */
    @OWLObjectProperty(iri = SKOS.BROADER, fetch = FetchType.EAGER, cascade = {CascadeType.DETACH})
    private Set<Term> parentTerms;

    /**
     * Parent terms from different vocabularies.
     * <p>
     * Represents the {@code skos:broadMatch} property.
     */
    @JsonIgnore
    @OWLObjectProperty(iri = SKOS.BROAD_MATCH, fetch = FetchType.EAGER, cascade = {CascadeType.DETACH})
    private Set<Term> externalParentTerms;

    public Term() {
    }

    public Term(URI uri) {
        setUri(uri);
    }

    public Set<Term> getParentTerms() {
        return parentTerms;
    }

    public void setParentTerms(Set<Term> parentTerms) {
        this.parentTerms = parentTerms;
    }

    public Set<Term> getExternalParentTerms() {
        return externalParentTerms;
    }

    public void setExternalParentTerms(Set<Term> externalParentTerms) {
        this.externalParentTerms = externalParentTerms;
    }

    /**
     * Adds the specified term to the parent terms of this instance.
     * <p>
     * If the specified term is from the same glossary, it is added to {@code parentTerms}, otherwise, it is added to
     * the {@code importedParentTerms}.
     *
     * @param term Term to add as parent
     */
    public void addParentTerm(Term term) {
        Objects.requireNonNull(term);
        if (!Objects.equals(getVocabulary(), term.getVocabulary())) {
            if (externalParentTerms == null) {
                setExternalParentTerms(new HashSet<>());
            }
            externalParentTerms.add(term);
        } else {
            if (parentTerms == null) {
                setParentTerms(new HashSet<>());
            }
            parentTerms.add(term);
        }
    }

    /**
     * Checks whether this term has a parent term in the same vocabulary.
     *
     * @return Whether this term has a parent in its vocabulary. Returns {@code false} also if this term has no parent
     * term at all.
     */
    public boolean hasParentInSameVocabulary() {
        return parentTerms != null && parentTerms.stream().anyMatch(p -> p.getVocabulary().equals(getVocabulary()));
    }

    /**
     * Consolidates parent and external parent terms into just parent terms.
     * <p>
     * This is based on the fact that external parents are a special case of parent terms (SKOS broadMatch is a
     * sub-property of broader). Clients need not know about their distinction, which is important only at repository
     * level.
     *
     * @see #splitExternalAndInternalParents()
     */
    public void consolidateParents() {
        if (externalParentTerms != null && !externalParentTerms.isEmpty()) {
            if (parentTerms == null) {
                setParentTerms(new LinkedHashSet<>());
            }
            parentTerms.addAll(externalParentTerms);
        }
    }

    /**
     * Splits consolidated parent terms into external and (internal) parent terms.
     * <p>
     * This split is driven by the fact that external parents belong to a different glossary than this term and should
     * thus be differentiated on repository level.
     * <p>
     * This method does the inverse of {@link #consolidateParents()}.
     *
     * @see #consolidateParents()
     */
    public void splitExternalAndInternalParents() {
        if (parentTerms == null || parentTerms.isEmpty()) {
            return;
        }
        final Set<Term> parents = new LinkedHashSet<>();
        final Set<Term> externalParents = new LinkedHashSet<>();
        for (Term p : parentTerms) {
            if (Objects.equals(getVocabulary(), p.getVocabulary())) {
                parents.add(p);
            } else {
                externalParents.add(p);
            }
        }
        this.parentTerms = parents;
        this.externalParentTerms = externalParents;
    }
}
