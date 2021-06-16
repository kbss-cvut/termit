package cz.cvut.kbss.termit.exception;

/**
 * Indicates that the type of data provided for import cannot be used.
 */
public class UnsupportedImportMediaTypeException extends VocabularyImportException {

    public UnsupportedImportMediaTypeException(String message) {
        super(message);
    }
}
