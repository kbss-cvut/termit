package cz.cvut.kbss.termit.exception;

/**
 * Indicates that an unsupported operation has been attempted.
 */
public class UnsupportedOperationException extends TermItException {

    public UnsupportedOperationException(String message) {
        super(message);
    }

    public UnsupportedOperationException(String message, boolean suppressLogging) {
        super(message, suppressLogging);
    }
}
