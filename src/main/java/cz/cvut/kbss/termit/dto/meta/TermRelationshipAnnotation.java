package cz.cvut.kbss.termit.dto.meta;

import cz.cvut.kbss.jopa.model.annotations.ConstructorResult;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.model.annotations.SparqlResultSetMapping;
import cz.cvut.kbss.jopa.model.annotations.VariableResult;
import cz.cvut.kbss.jopa.model.annotations.util.NonEntity;
import cz.cvut.kbss.jopa.vocabulary.RDF;
import cz.cvut.kbss.termit.dto.RdfStatement;
import cz.cvut.kbss.termit.util.Utils;
import cz.cvut.kbss.termit.util.Vocabulary;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.net.URI;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@SparqlResultSetMapping(name = "TermRelationshipAnnotation", classes = @ConstructorResult(
        targetClass = TermRelationshipAnnotation.class,
        variables = {
                @VariableResult(name = "subject", type = URI.class),
                @VariableResult(name = "predicate", type = URI.class),
                @VariableResult(name = "object", type = URI.class),
                @VariableResult(name = "attribute", type = URI.class),
                @VariableResult(name = "value", type = Object.class)
        }
))
@NonEntity
@OWLClass(iri = Vocabulary.ONTOLOGY_IRI_TERMIT + "/TermRelationshipAnnotation")
public class TermRelationshipAnnotation implements Serializable {

    @NotNull
    @OWLObjectProperty(iri = RDF.SUBJECT)
    private RdfStatement relationship;

    @NotNull
    @OWLObjectProperty(iri = RDF.PREDICATE)
    private URI attribute;

    @OWLObjectProperty(iri = RDF.OBJECT)
    private Set<Object> value;

    public TermRelationshipAnnotation() {
    }

    /**
     * Constructor used by {@link SparqlResultSetMapping}.
     */
    public TermRelationshipAnnotation(URI subject, URI predicate, URI object, URI attribute, Object value) {
        this.relationship = new RdfStatement(subject, predicate, object);
        this.attribute = attribute;
        this.value = new HashSet<>(Set.of(value));
    }

    public TermRelationshipAnnotation(RdfStatement relationship, URI attribute, Object value) {
        this.relationship = relationship;
        this.attribute = attribute;
        this.value = new HashSet<>(Set.of(value));
    }

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

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TermRelationshipAnnotation that)) {
            return false;
        }
        return Objects.equals(relationship, that.relationship) && Objects.equals(attribute,
                                                                                 that.attribute) && Objects.equals(
                value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(relationship, attribute, value);
    }

    @Override
    public String toString() {
        return "<< " + relationship + " >> " + Utils.uriToString(attribute) + value;
    }
}
