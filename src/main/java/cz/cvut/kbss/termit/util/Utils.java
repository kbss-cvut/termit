package cz.cvut.kbss.termit.util;

import cz.cvut.kbss.termit.exception.TermItException;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

public class Utils {

    private Utils() {
        throw new AssertionError();
    }

    /**
     * Returns an empty collection if the specified collection is {@code null}. Otherwise, the collection itself is
     * returned.
     *
     * @param collection The collection to check
     * @return Non-null collection
     */
    public static <T> Collection<T> emptyIfNull(Collection<T> collection) {
        return collection == null ? Collections.emptySet() : collection;
    }

    /**
     * Loads query from the specified file.
     * <p>
     * The query should be in the query directory specified by {@link Constants#QUERY_DIRECTORY}.
     *
     * @param queryFileName Name of the query file
     * @return Query string read from the file
     */
    public static String loadQuery(String queryFileName) {
        final InputStream is = Utils.class.getClassLoader().getResourceAsStream(
                Constants.QUERY_DIRECTORY + File.separator + queryFileName);
        if (is == null) {
            throw new TermItException(
                    "Initialization exception. Query file not found in " + Constants.QUERY_DIRECTORY +
                            File.separator + queryFileName);
        }
        try (final BufferedReader in = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return in.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new TermItException("Initialization exception. Unable to load query!", e);
        }
    }
}
