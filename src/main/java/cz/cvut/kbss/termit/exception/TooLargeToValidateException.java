package cz.cvut.kbss.termit.exception;

/**
 * Indicates that a vocabulary cannot be validated because it is too large.
 */
public class TooLargeToValidateException extends TermItException {

    public TooLargeToValidateException(String message) {
        super(message);
    }
}
