package cz.cvut.kbss.termit.dto.rdf;

import org.eclipse.rdf4j.model.Literal;

public interface RdfValue {

    boolean isIri();

    boolean isLiteral();

    boolean isRdfTriple();

    default Iri asIri() {
        throw new UnsupportedOperationException();
    }

    default RdfTriple asRdfTriple() {
        throw new UnsupportedOperationException();
    }

    default RdfLiteral asLiteral() {
        throw new UnsupportedOperationException();
    }

    static RdfValue create(Object object) {
        try {
            return RdfResource.create(object);
        } catch (IllegalArgumentException e) {
            // value is not RDF resource
        }
        if (object instanceof RdfLiteral literal) {
            return literal;
        }
        if (object instanceof Literal literal) {
            return new RdfLiteral(literal);
        }
        throw new IllegalArgumentException(object.getClass().getName() + " is not a valid RdfValue");
    }
}
