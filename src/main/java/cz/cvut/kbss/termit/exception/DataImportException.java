package cz.cvut.kbss.termit.exception;

/**
 * Indicates an issue with importing vocabulary data.
 */
public class DataImportException extends TermItException {

    public DataImportException(String message) {
        super(message);
    }

    public DataImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
