package cz.cvut.kbss.termit.model.collection;

import cz.cvut.kbss.jopa.model.annotations.FetchType;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.model.annotations.RDFCollection;
import cz.cvut.kbss.jopa.vocabulary.SKOS;

import java.net.URI;
import java.util.List;

@OWLClass(iri = SKOS.ORDERED_COLLECTION)
public class OrderedTermCollection extends SkosCollection<List<URI>> {

    @RDFCollection
    @OWLObjectProperty(iri = SKOS.MEMBER_LIST, fetch = FetchType.EAGER)
    private List<URI> members;

    public OrderedTermCollection() {
        this.addType(SKOS.COLLECTION);
    }

    @Override
    public List<URI> getMembers() {
        return members;
    }

    @Override
    public void setMembers(List<URI> members) {
        this.members = members;
    }

    @Override
    public boolean isOrdered() {
        return true;
    }
}
