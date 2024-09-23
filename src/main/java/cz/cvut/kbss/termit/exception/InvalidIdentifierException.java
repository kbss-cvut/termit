package cz.cvut.kbss.termit.exception;

public class InvalidIdentifierException extends TermItException {

    public InvalidIdentifierException(String message, String messageId) {
        super(message, messageId);
    }

    public InvalidIdentifierException(String message, Throwable cause, String messageId) {
        super(message, cause, messageId);
    }
}
