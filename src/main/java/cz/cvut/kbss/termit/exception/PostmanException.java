package cz.cvut.kbss.termit.exception;

/**
 * Indicates that sending of an email has failed.
 */
public class PostmanException extends TermItException {

    public PostmanException(String message) {
        super(message);
    }

    public PostmanException(String message, Throwable cause) {
        super(message, cause);
    }
}
