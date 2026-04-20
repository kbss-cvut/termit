package cz.cvut.kbss.termit.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import cz.cvut.kbss.jopa.model.annotations.FetchType;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.TermInfo;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Flat term representation.
 * <p>
 * This is a special case of {@link Term} where we only want to access its direct parents, not its ancestors.
 * <p>
 * This class should be used primarily for read-only operations.
 *
 * @see Term
 */
@OWLClass(iri = SKOS.CONCEPT)
public class FlatTerm extends AbstractFullTerm {

    /**
     * Parent terms from the same vocabulary.
     */
    @OWLObjectProperty(iri = SKOS.BROADER, fetch = FetchType.EAGER)
    private Set<TermInfo> parentTerms;

    /**
     * Parent terms from different vocabularies.
     * <p>
     * Represents the {@code skos:broadMatch} property.
     */
    @JsonIgnore
    @OWLObjectProperty(iri = SKOS.BROAD_MATCH, fetch = FetchType.EAGER)
    private Set<TermInfo> externalParentTerms;

    public Set<TermInfo> getParentTerms() {
        return parentTerms;
    }

    public void setParentTerms(Set<TermInfo> parentTerms) {
        this.parentTerms = parentTerms;
    }

    public Set<TermInfo> getExternalParentTerms() {
        return externalParentTerms;
    }

    public void setExternalParentTerms(Set<TermInfo> externalParentTerms) {
        this.externalParentTerms = externalParentTerms;
    }

    /**
     * Consolidates parent and external parent terms into just parent terms.
     * <p>
     * This is based on the fact that external parents are a special case of parent terms (SKOS broadMatch is a
     * sub-property of broader). Clients need not know about their distinction, which is important only at repository
     * level.
     */
    public void consolidateParents() {
        if (externalParentTerms != null && !externalParentTerms.isEmpty()) {
            if (parentTerms == null) {
                setParentTerms(new LinkedHashSet<>());
            }
            parentTerms.addAll(externalParentTerms);
        }
    }
}
