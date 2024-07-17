package cz.cvut.kbss.termit.exception;

public class InvalidPasswordChangeRequestException extends AuthorizationException {

    private final String messageId;
    public InvalidPasswordChangeRequestException(String message, String messageId) {
        super(message);
        this.messageId = messageId;
    }

    public String getMessageId() {
        return messageId;
    }
}
