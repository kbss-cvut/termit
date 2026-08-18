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

public class FullTermDtoWithParents extends Term {
    /**
     * Parent terms from the same vocabulary.
     */
    @JsonProperty("parentTerms")
    @OWLObjectProperty(iri = SKOS.BROADER, fetch = FetchType.EAGER, cascade = {CascadeType.DETACH})
    private Set<TermInfoWithParents> fullParentTerms;

    /**
     * @see #getFullParentTerms()
     */
    @JsonIgnore
    @Override
    public Set<TermInfo> getParentTerms() {
        throw new UnsupportedOperationException("FullTermDtoWithParents must not contain simple TermInfo parent terms");
    }

    /**
     * @see #setFullParentTerms(Set) ()
     */
    @Override
    public void setParentTerms(Set<TermInfo> parentTerms) {
        throw new UnsupportedOperationException("FullTermDtoWithParents must not contain simple TermInfo parent terms");
    }

    @JsonIgnore
    @Override
    public Set<TermInfo> getExternalParentTerms() {
        throw new UnsupportedOperationException("FullTermDtoWithParents must not contain simple TermInfo parent terms");
    }

    @Override
    public void setExternalParentTerms(Set<TermInfo> externalParentTerms) {
        throw new UnsupportedOperationException("FullTermDtoWithParents must not contain simple TermInfo parent terms");
    }


    /**
     * Unsupported operation
     * @throws UnsupportedOperationException always
     */
    @Override
    public void addParentTerm(Term term) {
        throw new UnsupportedOperationException("FullTermDtoWithParents does not support adding individual parents");
    }

    /**
     * Checks whether this term has a parent term in the same vocabulary.
     *
     * @return Whether this term has a parent in its vocabulary. Returns {@code false} also if this term has no parent
     * term at all.
     */
    @Override
    public boolean hasParentInSameVocabulary() {
        return fullParentTerms != null && fullParentTerms.stream().anyMatch(p -> p.getVocabulary().equals(getVocabulary()));
    }

    /**
     * Unsupported operation
     * @throws UnsupportedOperationException always
     */
    @Override
    public void consolidateParents() {
        throw new UnsupportedOperationException("FullTermDtoWithParents does not support parents consolidation");
    }

    /**
     * Unsupported operation
     * @throws UnsupportedOperationException always
     */
    @Override
    public void splitExternalAndInternalParents() {
        throw new UnsupportedOperationException("FullTermDtoWithParents does not support parents consolidation");
    }

    public Set<TermInfoWithParents> getFullParentTerms() {
        return fullParentTerms;
    }

    public void setFullParentTerms(Set<TermInfoWithParents> fullParentTerms) {
        this.fullParentTerms = fullParentTerms;
    }
}
