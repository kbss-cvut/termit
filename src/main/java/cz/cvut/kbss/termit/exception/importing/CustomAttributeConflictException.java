package cz.cvut.kbss.termit.exception.importing;

import cz.cvut.kbss.termit.exception.InvalidParameterException;
import cz.cvut.kbss.termit.util.Utils;

import java.net.URI;
import java.util.Map;

/** Indicates that a custom attribute with the matching identifier exists but its domain or range are different. */
public class CustomAttributeConflictException extends InvalidParameterException {
    public CustomAttributeConflictException(boolean rangeMatches, boolean domainMatches, URI customAttributeUri) {
        final String property = getUnmatchingProperty(rangeMatches, domainMatches);
        super(
                "Custom attribute " + Utils.uriToString(customAttributeUri)
                        + " with the same identifier exists with a different "
                        + property,
                null,
                "error.customAttribute.conflict",
                Map.of("property", property));
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
