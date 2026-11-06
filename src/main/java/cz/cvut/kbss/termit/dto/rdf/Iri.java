package cz.cvut.kbss.termit.dto.rdf;

import com.fasterxml.jackson.annotation.JsonValue;
import org.eclipse.rdf4j.model.IRI;

import java.net.URI;

public record Iri(@JsonValue URI value) implements RdfResource {

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
