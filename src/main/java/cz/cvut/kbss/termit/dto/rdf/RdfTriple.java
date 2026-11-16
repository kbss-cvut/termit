package cz.cvut.kbss.termit.dto.rdf;

import cz.cvut.kbss.termit.util.Utils;

import java.util.Objects;

public class RdfTriple implements RdfResource {

    private RdfResource subject;

    private Iri predicate;

    private RdfValue object;

    private Iri context;

    public RdfTriple() {}

    public RdfTriple(RdfResource subject, Iri predicate, Object object, Iri context) {
        this.subject = Objects.requireNonNull(subject);
        this.predicate = Objects.requireNonNull(predicate);
        this.object = RdfValue.create(Objects.requireNonNull(object));
        this.context = context;
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

    public RdfValue getObject() {
        return object;
    }

    public void setObject(RdfValue object) {
        this.object = object;
    }

    public Iri getContext() {
        return context;
    }

    public void setContext(Iri context) {
        this.context = context;
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
