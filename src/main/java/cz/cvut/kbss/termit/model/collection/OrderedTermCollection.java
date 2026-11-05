package cz.cvut.kbss.termit.model.collection;

import cz.cvut.kbss.jopa.model.annotations.FetchType;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.model.annotations.RDFCollection;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.model.changetracking.IgnoreChanges;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@OWLClass(iri = SKOS.ORDERED_COLLECTION)
public class OrderedTermCollection extends SkosCollection<List<URI>> {

    /**
     * List of members in their defined order.
     *
     * @implNote Changes are explicitly tracked by
     *     {@link cz.cvut.kbss.termit.service.changetracking.CollectionMemberChangeCalculator
     *     CollectionMemberChangeCalculator}
     */
    @IgnoreChanges
    @RDFCollection
    @OWLObjectProperty(iri = SKOS.MEMBER_LIST, fetch = FetchType.EAGER)
    private List<URI> members;

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

    @Override
    public void addMember(URI member) {
        if (getMembers() == null || getMembers().isEmpty()) {
            setMembers(new ArrayList<>());
        }
        if (!hasMember(member)) {
            getMembers().addFirst(member);
        }
    }
}
