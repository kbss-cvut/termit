package cz.cvut.kbss.termit.dto;

import java.net.URI;
import java.util.Set;

public class TermBatchEditDto {

    private Set<URI> targetTerms;
    private Set<URI> related;
    private Set<URI> relatedMatch;
    private Set<URI> exactMatchTerms;
    private Set<URI> parentTerms;
    private Set<String> types;

    public Set<URI> getTargetTerms() {
        return targetTerms;
    }

    public void setTargetTerms(Set<URI> targetTerms) {
        this.targetTerms = targetTerms;
    }

    public Set<URI> getRelated() {
        return related;
    }

    public void setRelated(Set<URI> related) {
        this.related = related;
    }

    public Set<URI> getRelatedMatch() {
        return relatedMatch;
    }

    public void setRelatedMatch(Set<URI> relatedMatch) {
        this.relatedMatch = relatedMatch;
    }

    public Set<URI> getExactMatchTerms() {
        return exactMatchTerms;
    }

    public void setExactMatchTerms(Set<URI> exactMatchTerms) {
        this.exactMatchTerms = exactMatchTerms;
    }

    public Set<URI> getParentTerms() {
        return parentTerms;
    }

    public void setParentTerms(Set<URI> parentTerms) {
        this.parentTerms = parentTerms;
    }

    public Set<String> getTypes() {
        return types;
    }

    public void setTypes(Set<String> types) {
        this.types = types;
    }
}
