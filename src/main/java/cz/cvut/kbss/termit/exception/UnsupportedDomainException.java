package cz.cvut.kbss.termit.exception;

/**
 * Indicates that the specified domain is not supported for an attribute/property.
 */
public class UnsupportedDomainException extends TermItException {

    public UnsupportedDomainException(String message) {
        super(message);
    }
}
