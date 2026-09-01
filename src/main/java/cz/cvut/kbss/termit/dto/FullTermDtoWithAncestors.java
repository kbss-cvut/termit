package cz.cvut.kbss.termit.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import cz.cvut.kbss.jopa.model.annotations.CascadeType;
import cz.cvut.kbss.jopa.model.annotations.FetchType;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.TermInfoWithParents;

import java.util.Set;

/**
 * {@link Term} representation with blocked access to simple {@link #parentTerms parent} and {@link #externalParentTerms external parent}
 * terms.
 * Provides {@link #ancestorTerms} as a replacement for parent terms field.
 */
public class FullTermDtoWithAncestors extends Term {
    /**
     * Ancestors from the same vocabulary.
     */
    @JsonProperty("parentTerms")
    @OWLObjectProperty(iri = SKOS.BROADER, fetch = FetchType.EAGER, cascade = {CascadeType.DETACH})
    private Set<TermInfoWithParents> ancestorTerms;

    /**
     * Unsupported operation
     * @throws UnsupportedOperationException always
     * @see #getAncestorTerms()
     */
    @JsonIgnore
    @Override
    public Set<TermInfo> getParentTerms() {
        throw new UnsupportedOperationException("FullTermDtoWithAncestors must not contain simple TermInfo parent terms");
    }

    /**
     * Unsupported operation
     * @throws UnsupportedOperationException always
     * @see #setAncestorTerms(Set) ()
     */
    @Override
    public void setParentTerms(Set<TermInfo> parentTerms) {
        throw new UnsupportedOperationException("FullTermDtoWithAncestors must not contain simple TermInfo parent terms");
    }

    /**
     * Unsupported operation
     * @throws UnsupportedOperationException always
     */
    @JsonIgnore
    @Override
    public Set<TermInfo> getExternalParentTerms() {
        throw new UnsupportedOperationException("FullTermDtoWithAncestors must not contain simple TermInfo parent terms");
    }

    /**
     * Unsupported operation
     * @throws UnsupportedOperationException always
     */
    @Override
    public void setExternalParentTerms(Set<TermInfo> externalParentTerms) {
        throw new UnsupportedOperationException("FullTermDtoWithAncestors must not contain simple TermInfo parent terms");
    }


    /**
     * Unsupported operation
     * @throws UnsupportedOperationException always
     */
    @Override
    public void addParentTerm(Term term) {
        throw new UnsupportedOperationException("FullTermDtoWithAncestors does not support adding individual parents");
    }

    /**
     * Checks whether this term has an ancestor in the same vocabulary.
     *
     * @return Whether this term has an ancestor in its vocabulary. Returns {@code false} also if this term has no ancestor
     * at all.
     */
    @Override
    public boolean hasParentInSameVocabulary() {
        return ancestorTerms != null && ancestorTerms.stream().anyMatch(p -> p.getVocabulary().equals(getVocabulary()));
    }

    /**
     * Unsupported operation
     * @throws UnsupportedOperationException always
     */
    @Override
    public void consolidateParents() {
        throw new UnsupportedOperationException("FullTermDtoWithAncestors does not support parents consolidation");
    }

    /**
     * Unsupported operation
     * @throws UnsupportedOperationException always
     */
    @Override
    public void splitExternalAndInternalParents() {
        throw new UnsupportedOperationException("FullTermDtoWithAncestors does not support parents consolidation");
    }

    public Set<TermInfoWithParents> getAncestorTerms() {
        return ancestorTerms;
    }

    public void setAncestorTerms(Set<TermInfoWithParents> fullParentTerms) {
        this.ancestorTerms = fullParentTerms;
    }
}
