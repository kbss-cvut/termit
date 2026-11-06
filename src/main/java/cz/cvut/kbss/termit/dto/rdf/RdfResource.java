package cz.cvut.kbss.termit.dto.rdf;

public interface RdfResource {

    boolean isRdfTriple();

    boolean isIri();

    default Iri asIri() {
        throw new UnsupportedOperationException();
    }

    default RdfTriple asRdfTriple() {
        throw new UnsupportedOperationException();
    }
}
