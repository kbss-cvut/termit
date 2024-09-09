package cz.cvut.kbss.termit.exception;

public class InvalidPasswordChangeRequestException extends AuthorizationException {

    public InvalidPasswordChangeRequestException(String message, String messageId) {
        super(message, messageId);
    }
}
