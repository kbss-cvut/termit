package cz.cvut.kbss.termit.exception;

/**
 * Indicates that the application was unable to generate a selector.
 */
public class SelectorGenerationException extends AnnotationGenerationException {

    public SelectorGenerationException(String message) {
        super(message);
    }
}
