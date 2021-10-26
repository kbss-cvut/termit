/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If not, see
 * <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.service;

import cz.cvut.kbss.termit.exception.TermItException;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Objects;

/**
 * Service for generating and resolving identifiers.
 */
@Service
public class IdentifierResolver {

    private static final char REPLACEMENT_CHARACTER = '-';
    private static final int[] ILLEGAL_FILENAME_CHARS = {34,
                                                         60,
                                                         62,
                                                         124,
                                                         0,
                                                         1,
                                                         2,
                                                         3,
                                                         4,
                                                         5,
                                                         6,
                                                         7,
                                                         8,
                                                         9,
                                                         10,
                                                         11,
                                                         12,
                                                         13,
                                                         14,
                                                         15,
                                                         16,
                                                         17,
                                                         18,
                                                         19,
                                                         20,
                                                         21,
                                                         22,
                                                         23,
                                                         24,
                                                         25,
                                                         26,
                                                         27,
                                                         28,
                                                         29,
                                                         30,
                                                         31,
                                                         58,
                                                         42,
                                                         63,
                                                         92,
                                                         47};

    static {
        Arrays.sort(ILLEGAL_FILENAME_CHARS);
    }

    /**
     * Normalizes the specified value which includes:
     * <ul>
     * <li>Transforming the value to lower case</li>
     * <li>Trimming the string</li>
     * <li>Replacing white spaces and slashes with dashes</li>
     * <li>Removing parentheses</li>
     * </ul>
     * <p>
     * Based on <a href="https://gist.github.com/rponte/893494">https://gist.github.com/rponte/893494</a>
     *
     * @param value The value to normalize
     * @return Normalized string
     */
    public static String normalize(String value) {
        Objects.requireNonNull(value);
        final String normalized = value.toLowerCase().trim()
                                       .replaceAll("[\\s/\\\\]", Character.toString(REPLACEMENT_CHARACTER));
        return normalized.replaceAll("[(?&)]", "");
    }

    /**
     * Normalizes the specified value which includes operations performed by {@link #normalize(String)} + replacing
     * non-ASCII characters with ASCII ones (e.g., 'č' with 'c').
     *
     * @param value The value to normalize
     * @return Normalized string
     */
    public static String normalizeToAscii(String value) {
        return Normalizer.normalize(normalize(value), Normalizer.Form.NFD).replaceAll("[^\\p{ASCII}]", "");
    }

    /**
     * Generates identifier, appending a normalized string consisting of the specified components to the namespace.
     *
     * @param namespace  Base URI for the generation
     * @param components Components to normalize and add to the identifier
     * @return Generated identifier
     */
    public URI generateIdentifier(String namespace, String... components) {
        Objects.requireNonNull(namespace);
        if (components.length == 0) {
            throw new IllegalArgumentException("Must provide at least one component for identifier generation.");
        }
        final String comps = String.join("-", components);
        if (isUri(comps)) {
            final URI tempUri = URI.create(comps);
            try {
                return new URI(tempUri.getScheme(), tempUri.getAuthority(), tempUri.getPath(), null,
                        tempUri.getFragment());
            } catch (URISyntaxException e) {
                // Shouldn't happen, URI has been already validated using URI.create
                throw new TermItException(e);
            }
        }
        if (!namespace.endsWith("/") && !namespace.endsWith("#")) {
            namespace += "/";
        }
        return URI.create(namespace + normalize(comps));
    }

    private static boolean isUri(String value) {
        try {
            if (!value.matches("^(https?|ftp|file)://.+")) {
                return false;
            }
            URI.create(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Generates term identifier of a dependent asset, appending a normalized string consisting of the specified
     * components to a namespace which is derived from baseUri by appending namespace separator defined by
     * namespaceSeparatorConfig.
     *
     * @param baseUri Configuration parameter for namespace
     * @param label   Components to normalize and add to the identifier
     * @return Generated identifier
     */
    public URI generateDerivedIdentifier(URI baseUri, String namespaceSeparator, String label) {
        return generateIdentifier(buildNamespace(baseUri.toString(),
                        namespaceSeparator),
                label
        );
    }

    /**
     * Builds an identifier from the specified namespace and fragment.
     * <p>
     * This method assumes that the fragment is a normalized string uniquely identifying a resource in the specified
     * namespace.
     * <p>
     * Basically, the returned identifier should be the same as would be returned for non-normalized fragments by {@link
     * #generateIdentifier(String, String...)}.
     *
     * @param namespace Identifier namespace
     * @param fragment  Normalized string unique in the specified namespace
     * @return Identifier
     */
    public URI resolveIdentifier(String namespace, String fragment) {
        Objects.requireNonNull(namespace);
        if (!namespace.endsWith("/") && !namespace.endsWith("#")) {
            namespace += "/";
        }
        return URI.create(namespace + fragment);
    }

    /**
     * Creates a namespace URI by appending the specified components to the specified base URI, adding separators where
     * necessary.
     *
     * @param baseUri    Base URI for the namespace
     * @param components Components to add to namespace URI. Should be normalized
     * @return Namespace URI, ending with a forward slash
     */
    public String buildNamespace(String baseUri, String... components) {
        Objects.requireNonNull(baseUri);
        final StringBuilder sb = new StringBuilder(baseUri);
        for (String comp : components) {
            if (sb.charAt(sb.length() - 1) != '/' && comp.charAt(0) != '/') {
                sb.append('/');
            }
            sb.append(comp);
        }
        if (sb.charAt(sb.length() - 1) != '/') {
            sb.append('/');
        }
        return sb.toString();
    }

    /**
     * Extracts locally unique identifier fragment from the specified URI.
     *
     * @param uri URI to extract fragment from
     * @return Identification fragment
     */
    public static String extractIdentifierFragment(URI uri) {
        Objects.requireNonNull(uri);
        final String strUri = uri.toString();
        final int slashIndex = strUri.lastIndexOf('/');
        final int hashIndex = strUri.lastIndexOf('#');
        return strUri.substring((Math.max(slashIndex, hashIndex)) + 1);
    }

    /**
     * Extracts namespace from the specified URI.
     * <p>
     * Namespace in this case means the part of the URI up to the last forward slash or hash tag, whichever comes
     * later.
     *
     * @param uri URI to extract namespace from
     * @return Identifier namespace
     */
    public static String extractIdentifierNamespace(URI uri) {
        final String strUri = uri.toString();
        final int slashIndex = strUri.lastIndexOf('/');
        final int hashIndex = strUri.lastIndexOf('#');
        return strUri.substring(0, (Math.max(slashIndex, hashIndex)) + 1);
    }

    /**
     * Sanitizes the specified label so that it can be used as a file name.
     * <p>
     * This means replacing illegal characters (e.g., slashes) with dashes.
     *
     * @param label Label to sanitize to a valid file name
     * @return Valid file name based on the specified label
     */
    public static String sanitizeFileName(String label) {
        StringBuilder cleanName = new StringBuilder();
        for (int i = 0; i < label.length(); i++) {
            int c = label.charAt(i);
            if (Arrays.binarySearch(ILLEGAL_FILENAME_CHARS, c) < 0) {
                cleanName.append((char) c);
            } else {
                cleanName.append(REPLACEMENT_CHARACTER);
            }
        }
        return cleanName.toString().trim();
    }
}
