package cz.cvut.kbss.termit.dto.listing;

import cz.cvut.kbss.jopa.model.annotations.FetchType;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.model.AbstractTerm;

import java.net.URI;
import java.util.Set;

@OWLClass(iri = SKOS.CONCEPT)
public class FlatTermDto extends AbstractTerm {

    @OWLObjectProperty(iri = SKOS.BROADER, fetch = FetchType.EAGER)
    private Set<URI> parentTerms;

    public Set<URI> getParentTerms() {
        return parentTerms;
    }

    public void setParentTerms(Set<URI> parentTerms) {
        this.parentTerms = parentTerms;
    }
}
