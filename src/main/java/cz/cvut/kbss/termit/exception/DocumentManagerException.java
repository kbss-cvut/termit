package cz.cvut.kbss.termit.exception;

/**
 * Indicates an error when processing content of files.
 */
public class DocumentManagerException extends TermItException {

    public DocumentManagerException(String message) {
        super(message);
    }

    public DocumentManagerException(String message, Throwable cause) {
        super(message, cause);
    }
}
