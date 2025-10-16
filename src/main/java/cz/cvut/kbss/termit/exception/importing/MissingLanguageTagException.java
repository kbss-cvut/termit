package cz.cvut.kbss.termit.exception.importing;

/**
 * Indicates that a property value is missing required language.
 */
public class MissingLanguageTagException extends VocabularyImportException {

    public MissingLanguageTagException(String message, String messageId) {
        super(message, messageId);
    }
}
