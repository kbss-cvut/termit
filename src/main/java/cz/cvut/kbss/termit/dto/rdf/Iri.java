package cz.cvut.kbss.termit.dto.rdf;

import cz.cvut.kbss.jopa.model.annotations.Id;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.util.NonEntity;
import cz.cvut.kbss.jopa.vocabulary.RDFS;
import org.eclipse.rdf4j.model.IRI;

import java.net.URI;

@NonEntity
@OWLClass(iri = RDFS.RESOURCE)
public record Iri(@Id URI value) implements RdfResource {

    @Override
    public boolean isRdfTriple() {
        return false;
    }

    @Override
    public boolean isIri() {
        return true;
    }

    @Override
    public Iri asIri() {
        return this;
    }

    public static Iri create(IRI iri) {
        return new Iri(URI.create(iri.stringValue()));
    }
}
