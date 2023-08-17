package cz.cvut.kbss.termit.exception;

/**
 * Indicates an invalid attempt to set a term's state.
 * <p>
 * The attached message should contain information as to why the state was invalid.
 */
public class InvalidTermStateException extends TermItException {

    private final String messageId;

    public InvalidTermStateException(String message, String messageId) {
        super(message);
        this.messageId = messageId;
    }

    public String getMessageId() {
        return messageId;
    }
}
