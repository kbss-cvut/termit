package cz.cvut.kbss.termit.exception;

/**
 * Indicates that an unsupported facet was provided to faceted search.
 */
public class UnsupportedSearchFacetException extends TermItException {

    public UnsupportedSearchFacetException(String message) {
        super(message);
    }
}
