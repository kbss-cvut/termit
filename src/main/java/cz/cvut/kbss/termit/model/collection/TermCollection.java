package cz.cvut.kbss.termit.model.collection;

import cz.cvut.kbss.jopa.model.annotations.FetchType;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.model.annotations.PrePersist;
import cz.cvut.kbss.jopa.model.annotations.PreUpdate;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.exception.ValidationException;

import java.net.URI;
import java.util.Set;

/**
 * An unordered collection of {@link cz.cvut.kbss.termit.model.Term Terms} and other {@link SkosCollection SKOS
 * collections}.
 */
@OWLClass(iri = SKOS.COLLECTION)
public class TermCollection extends SkosCollection<Set<URI>> {

    @OWLObjectProperty(iri = SKOS.MEMBER, fetch = FetchType.EAGER)
    private Set<URI> members;

    @Override
    public Set<URI> getMembers() {
        return members;
    }

    @Override
    public void setMembers(Set<URI> members) {
        this.members = members;
    }

    @PrePersist
    @PreUpdate
    private void validateSave() {
        if (getTypes() != null && isOrdered()) {
            throw new ValidationException("Ordered collection must not be modified as unordered one!");
        }
    }
}
