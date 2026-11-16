package cz.cvut.kbss.termit.dto.rdf;

import cz.cvut.kbss.jopa.vocabulary.RDF;
import org.eclipse.rdf4j.model.Literal;

import java.net.URI;
import java.util.Optional;

public class RdfLiteral implements RdfValue {
    private final String label;
    private final String language;
    private final URI datatype;

    public RdfLiteral(String label, URI datatype) {
        this.label = label;
        this.language = null;
        this.datatype = datatype;
    }

    public RdfLiteral(String label, String language) {
        this.label = label;
        this.language = language;
        this.datatype = URI.create(RDF.LANG_STRING);
    }

    public RdfLiteral(Literal literal) {
        this.label = literal.getLabel();
        this.language = literal.getLanguage().orElse(null);
        this.datatype = URI.create(literal.getDatatype().stringValue());
    }

    @Override
    public boolean isRdfTriple() {
        return false;
    }

    @Override
    public boolean isIri() {
        return false;
    }

    @Override
    public boolean isLiteral() {
        return true;
    }

    @Override
    public RdfLiteral asLiteral() {
        return this;
    }

    /**
     * Gets the label (the lexical value) of this literal.
     *
     * @return The literal's label.
     */
    public String getLabel() {
        return label;
    }

    /**
     * Gets the language tag for this literal, normalized to lower case.
     *
     * @return The language tag for this literal, or {@link Optional#empty()} if it doesn't have one.
     */
    public Optional<String> getLanguage() {
        return Optional.ofNullable(language);
    }

    /**
     * Gets the datatype for this literal.
     *
     * <p>If {@link #getLanguage()} returns a non-empty value than this must return <a
     * href="http://www.w3.org/1999/02/22-rdf-syntax-ns#langString">{@code rdf:langString}</a>. If no datatype was
     * assigned to this literal by the creator, then this method must return <a
     * href="http://www.w3.org/2001/XMLSchema#string">{@code xsd:string}</a>.
     *
     * @return The datatype for this literal.
     */
    public URI getDatatype() {
        return datatype;
    }
}
