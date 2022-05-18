package cz.cvut.kbss.termit.exception.importing;

/**
 * Indicates that a vocabulary with the same identifier already exists and would be overridden by the new one.
 */
public class VocabularyExistsException extends VocabularyImportException {

    public VocabularyExistsException(String message) {
        super(message);
    }
}
