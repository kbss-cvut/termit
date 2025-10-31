package cz.cvut.kbss.termit.dto.meta;

import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.model.annotations.util.NonEntity;
import cz.cvut.kbss.jopa.vocabulary.RDF;
import cz.cvut.kbss.termit.dto.RdfStatement;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.io.Serializable;
import java.net.URI;
import java.util.Set;

@NonEntity
@OWLClass(iri = Vocabulary.ONTOLOGY_IRI_TERMIT + "/TermRelationshipAnnotation")
public class TermRelationshipAnnotation implements Serializable {

    @OWLObjectProperty(iri = RDF.SUBJECT)
    private RdfStatement relationship;

    @OWLObjectProperty(iri = RDF.PREDICATE)
    private URI attribute;

    @OWLObjectProperty(iri = RDF.OBJECT)
    private Set<Object> value;

    public RdfStatement getRelationship() {
        return relationship;
    }

    public void setRelationship(RdfStatement relationship) {
        this.relationship = relationship;
    }

    public URI getAttribute() {
        return attribute;
    }

    public void setAttribute(URI attribute) {
        this.attribute = attribute;
    }

    public Set<Object> getValue() {
        return value;
    }

    public void setValue(Set<Object> value) {
        this.value = value;
    }
}
