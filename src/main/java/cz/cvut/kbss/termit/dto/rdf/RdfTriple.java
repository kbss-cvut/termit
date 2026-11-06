package cz.cvut.kbss.termit.dto.rdf;

import cz.cvut.kbss.jopa.model.annotations.OWLAnnotationProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.model.annotations.util.NonEntity;
import cz.cvut.kbss.jopa.vocabulary.RDF;
import cz.cvut.kbss.termit.util.Utils;

import java.util.Objects;

@NonEntity
@OWLClass(iri = RDF.STATEMENT)
public class RdfTriple implements RdfResource {

    @OWLObjectProperty(iri = RDF.SUBJECT)
    private RdfResource subject;

    @OWLObjectProperty(iri = RDF.PREDICATE)
    private Iri predicate;

    @OWLAnnotationProperty(iri = RDF.OBJECT)
    private Object object;

    public RdfTriple() {}

    public RdfTriple(RdfResource subject, Iri predicate, Object object) {
        this.subject = Objects.requireNonNull(subject);
        this.predicate = Objects.requireNonNull(predicate);
        this.object = Objects.requireNonNull(object);
    }

    public RdfResource getSubject() {
        return subject;
    }

    public void setSubject(RdfResource subject) {
        this.subject = subject;
    }

    public Iri getPredicate() {
        return predicate;
    }

    public void setPredicate(Iri predicate) {
        this.predicate = predicate;
    }

    public Object getObject() {
        return object;
    }

    public void setObject(Object object) {
        this.object = object;
    }

    @Override
    public boolean isRdfTriple() {
        return true;
    }

    @Override
    public boolean isIri() {
        return false;
    }

    @Override
    public RdfTriple asRdfTriple() {
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof RdfTriple rdfTriple)) return false;
        return Objects.equals(subject, rdfTriple.subject)
                && Objects.equals(predicate, rdfTriple.predicate)
                && Objects.equals(object, rdfTriple.object);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subject, predicate, object);
    }

    @Override
    public String toString() {
        return subject.isIri()
                ? Utils.uriToString(subject.asIri().value())
                : "<< " + subject.asRdfTriple().toString() + " >>" + " " + Utils.uriToString(predicate.value()) + " "
                        + object;
    }
}
