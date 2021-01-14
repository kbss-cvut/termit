package cz.cvut.kbss.termit.exception;

/**
 * Indicates an issue with REST parameters semantics.
 */
public class InvalidParameterException extends TermItException {

    public InvalidParameterException(String message) {
        super(message);
    }
}
