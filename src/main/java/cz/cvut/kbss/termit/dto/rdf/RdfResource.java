package cz.cvut.kbss.termit.dto.rdf;

import org.eclipse.rdf4j.model.Resource;

import java.net.URI;

public interface RdfResource extends RdfValue {
    @Override
    default boolean isLiteral() {
        return false;
    }

    static RdfResource create(Object object) {
        if (object instanceof Iri iri) {
            return iri;
        }
        if (object instanceof URI uri) {
            return new Iri(uri);
        }
        if (object instanceof RdfTriple rdfTriple) {
            return rdfTriple;
        }
        if (object instanceof Resource resource) {
            return new Iri(URI.create(resource.stringValue()));
        }
        throw new IllegalArgumentException("Cannot create RdfResource from object " + object);
    }
}
