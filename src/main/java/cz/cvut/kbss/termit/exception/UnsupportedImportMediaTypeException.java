package cz.cvut.kbss.termit.exception;

/**
 * Indicates that the type of data provided for import cannot be used.
 */
public class UnsupportedImportMediaTypeException extends DataImportException {

    public UnsupportedImportMediaTypeException(String message) {
        super(message);
    }
}
