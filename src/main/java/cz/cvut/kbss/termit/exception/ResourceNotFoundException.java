package cz.cvut.kbss.termit.exception;

/**
 * Indicates that an internal resource (typically a file expected on classpath) was not found.
 */
public class ResourceNotFoundException extends TermItException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
