package cz.cvut.kbss.termit.dto.meta;

import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.model.annotations.util.NonEntity;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.jopa.vocabulary.RDF;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.net.URI;

@NonEntity
@OWLClass(iri = Vocabulary.ONTOLOGY_IRI_TERMIT + "/AnnotatedTermRelationship")
public class AnnotatedTermRelationship {

    @OWLObjectProperty(iri = RDF.SUBJECT)
    private TermInfo subject;

    @OWLObjectProperty(iri = RDF.PREDICATE)
    private URI property;

    @OWLObjectProperty(iri = RDF.OBJECT)
    private TermInfo object;

    @OWLObjectProperty(iri = DC.Terms.RELATION)
    private URI annotationProperty;

    public AnnotatedTermRelationship() {
    }

    public AnnotatedTermRelationship(TermInfo subject, URI property, TermInfo object, URI annotationProperty) {
        this.subject = subject;
        this.property = property;
        this.object = object;
        this.annotationProperty = annotationProperty;
    }

    public TermInfo getSubject() {
        return subject;
    }

    public void setSubject(TermInfo subject) {
        this.subject = subject;
    }

    public URI getProperty() {
        return property;
    }

    public void setProperty(URI property) {
        this.property = property;
    }

    public TermInfo getObject() {
        return object;
    }

    public void setObject(TermInfo object) {
        this.object = object;
    }

    public URI getAnnotationProperty() {
        return annotationProperty;
    }

    public void setAnnotationProperty(URI annotationProperty) {
        this.annotationProperty = annotationProperty;
    }
}
