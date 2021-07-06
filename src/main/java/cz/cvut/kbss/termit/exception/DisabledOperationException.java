package cz.cvut.kbss.termit.exception;

@SuppressibleLogging
public class DisabledOperationException extends TermItException {

    public DisabledOperationException(String message) {
        super(message);
    }
}
