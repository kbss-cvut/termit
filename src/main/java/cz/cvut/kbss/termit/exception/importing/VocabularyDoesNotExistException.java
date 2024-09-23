package cz.cvut.kbss.termit.exception.importing;

/**
 * Indicates that an existing vocabulary was expected for import but none was found.
 */
public class VocabularyDoesNotExistException extends VocabularyImportException {
    public VocabularyDoesNotExistException(String message) {
        super(message);
    }
}
