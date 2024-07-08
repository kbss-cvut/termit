package cz.cvut.kbss.termit.exception;

/**
 * Indicates an error when processing file content.
 */
public class FileContentProcessingException extends TermItException {

    public FileContentProcessingException(String message) {
        super(message);
    }

    public FileContentProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
