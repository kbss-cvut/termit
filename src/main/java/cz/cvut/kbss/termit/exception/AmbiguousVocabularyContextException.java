package cz.cvut.kbss.termit.exception;

/**
 * Indicates a mismatch between vocabularies and repository contexts.
 * <p>
 * This may be either that versions of a single vocabulary were found in multiple contexts, and it was not possible to
 * determine which one to use. Or that multiple vocabularies were found in the same context.
 */
public class AmbiguousVocabularyContextException extends TermItException {

    public AmbiguousVocabularyContextException(String message) {
        super(message);
    }
}
