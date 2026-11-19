package cz.cvut.kbss.termit.exception.importing;

import cz.cvut.kbss.termit.exception.InvalidParameterException;
import cz.cvut.kbss.termit.util.Utils;

import java.net.URI;

public class CustomAttributeConflictException extends InvalidParameterException {
    public CustomAttributeConflictException(boolean rangeMatches, boolean domainMatches, URI customAttributeUri) {
        super("Custom attribute " + Utils.uriToString(customAttributeUri)
                + " with the same identifier exists with a different "
                + getUnmatchingProperty(rangeMatches, domainMatches));
    }

    private static String getUnmatchingProperty(boolean rangeMatches, boolean domainMatches) {
        if (!rangeMatches) {
            return "range";
        }
        if (!domainMatches) {
            return "domain";
        }
        throw new IllegalArgumentException();
    }
}
