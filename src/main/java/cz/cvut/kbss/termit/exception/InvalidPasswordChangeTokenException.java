package cz.cvut.kbss.termit.exception;

public class InvalidPasswordChangeTokenException extends AuthorizationException {

    private final String messageId;
    public InvalidPasswordChangeTokenException(String message, String messageId) {
        super(message);
        this.messageId = messageId;
    }

    public String getMessageId() {
        return messageId;
    }
}
