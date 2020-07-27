package cz.cvut.kbss.termit.exception;

/**
 * Indicates that the definition source of a term has already been provided.
 */
public class TermDefinitionSourceExistsException extends TermItException {

    public TermDefinitionSourceExistsException(String message) {
        super(message);
    }
}
