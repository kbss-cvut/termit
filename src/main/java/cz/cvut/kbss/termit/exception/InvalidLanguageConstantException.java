package cz.cvut.kbss.termit.exception;

/**
 * Indicates an unknown individual expected to have belonged to a controlled language.
 */
public class InvalidLanguageConstantException extends TermItException {

    public InvalidLanguageConstantException(String message) {
        super(message);
    }
}
