package cz.cvut.kbss.termit.exception.importing;

/**
 * Indicates that a term from unrelated vocabulary was referenced in a vocabulary import in context where it was
 * expected that the vocabulary was imported.
 * <p>
 * For example, a parent term may be from a different vocabulary, but that vocabulary must be imported by the vocabulary
 * of the referring term.
 */
public class ReferencedTermInUnrelatedVocabularyException extends VocabularyImportException {

    public ReferencedTermInUnrelatedVocabularyException(String message, String messageId) {
        super(message, messageId);
    }
}
