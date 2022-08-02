package cz.cvut.kbss.termit.exception;

/**
 * Indicates that a vocabulary has been found in multiple repository contexts, and it was not possible to determine
 * which one to use.
 */
public class AmbiguousVocabularyContextException extends TermItException {

    public AmbiguousVocabularyContextException(String message) {
        super(message);
    }
}
