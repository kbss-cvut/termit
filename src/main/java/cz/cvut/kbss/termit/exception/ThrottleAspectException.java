package cz.cvut.kbss.termit.exception;

/**
 * Indicates wrong usage of {@link cz.cvut.kbss.termit.util.throttle.Throttle} annotation.
 *
 * @see cz.cvut.kbss.termit.util.throttle.ThrottleAspect
 */
public class ThrottleAspectException extends TermItException {

    public ThrottleAspectException(String message) {
        super(message);
    }

    public ThrottleAspectException(String message, Throwable cause) {
        super(message, cause);
    }
}
