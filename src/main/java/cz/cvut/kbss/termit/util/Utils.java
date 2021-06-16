package cz.cvut.kbss.termit.util;

import cz.cvut.kbss.termit.exception.TermItException;
import org.eclipse.rdf4j.model.*;
import org.eclipse.rdf4j.model.util.Statements;
import org.eclipse.rdf4j.model.util.Values;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
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

    /**
     * Changes IRI of a resource in a model.
     *
     * @param originalIri the IRI to change
     * @param newIri      the new, changed, IRI
     * @param model       model to change the IRI in
     */
    public static void changeIri(final String originalIri, final String newIri, final Model model) {
        changeIrisByFunction(oIri -> originalIri.equals(oIri), oIri -> newIri, model);
    }

    /**
     * Changes IRI of a resource in a model.
     *
     * @param originalNamespace the IRI to change
     * @param newNamespace      the new, changed, IRI
     * @param model             model to change the IRI in
     */
    public static void changeNamespace(final String originalNamespace,
                                       final String newNamespace,
                                       final Model model) {
        changeIrisByFunction(
            oIri -> oIri.startsWith(originalNamespace),
            oIri -> oIri.replaceFirst("^" + originalNamespace, newNamespace),
            model
        );
    }

    /**
     * Changes IRI of a resource in a model.
     *
     * @param checkOriginalIri checks whether the original IRI should be renamed
     * @param transformIri     transforms the original IRI to the new IRI
     * @param model            model to change the IRI in
     */
    private static void changeIrisByFunction(final Function<String, Boolean> checkOriginalIri,
                                             final Function<String, String> transformIri,
                                             final Model model) {
        final Collection<Statement> statementsToAdd = new HashSet<>();
        final Collection<Statement> statementsToRemove = new HashSet<>();
        model.getStatements(null, null, null).forEach(s -> {
            boolean changed = false;

            Resource subject = s.getSubject();
            if (s.getSubject().isIRI() && checkOriginalIri.apply(s.getSubject().stringValue())) {
                subject = Values.iri(transformIri.apply(s.getSubject().stringValue()));
                changed = true;
            }

            IRI predicate = s.getPredicate();
            if (checkOriginalIri.apply(s.getPredicate().stringValue())) {
                predicate = Values.iri(transformIri.apply(s.getPredicate().stringValue()));
                changed = true;
            }

            Value object = s.getObject();
            if (checkOriginalIri.apply(s.getObject().stringValue())) {
                object = Values.iri(transformIri.apply(s.getObject().stringValue()));
                changed = true;
            }

            if (changed) {
                statementsToAdd.add(Statements.statement(subject, predicate, object, s.getContext()));
                statementsToRemove.add(Statements.statement(s.getSubject(), s.getPredicate(), s.getObject(), s.getContext()));
            }
        });
        model.removeAll(statementsToRemove);
        model.addAll(statementsToAdd);
    }

    /**
     * Extracts the necessary vocabulary IRI from the namespace of the current concepts.
     *
     * @param conceptUris set of concept IRIs
     * @param termSeparator separator between a term local name and vocabulary IRI
     * @return IRI of the vocabulary
     * @throws IllegalArgumentException if the namespace is not unique in concept IRIs, there is no concept,
     * or the concept IRI does not have the form "pojem".
     */
    public static String getVocabularyIri(final Set<String> conceptUris, String termSeparator) {
        if (conceptUris.isEmpty()) {
            throw new IllegalArgumentException("No namespace candidate.");
        }

        final Iterator<String> i = conceptUris.iterator();

        final String conceptUri = i.next();

        final String separator;

        if (conceptUri.lastIndexOf(termSeparator) > 0) {
            separator = termSeparator;
        } else if (conceptUri.lastIndexOf("#") > 0) {
            separator = "#";
        } else if (conceptUri.lastIndexOf("/") > 0) {
            separator = "/";
        } else {
            throw new IllegalArgumentException("The IRI does not have a proper format: " + conceptUri);
        }

        final String namespace = conceptUri.substring(0, conceptUri.lastIndexOf(separator));

        for (final String s : conceptUris) {
            if (!s.startsWith(namespace)) {
                throw new IllegalArgumentException("Not all Concept IRIs have the same namespace: " + conceptUri + " vs. " + namespace);
            }
        }
        return namespace;
    }

    /**
     * Gets unique IRI from the base string.
     * <p>
     * This method checks whether base is unique (fails the getAsset test). If not, the IRI is extended with numbers
     * until it gets unique.
     *
     * @param base     base string to derive the IRI from
     * @param getAsset checker function to see
     * @param <T>      type of the asset
     * @return
     */
    public static <T> String getUniqueIriFromBase(final String base, final Function<String, Optional<T>> getAsset) {
        int i = 0;
        String iri = base;
        Optional<T> possiblyAsset = getAsset.apply(iri);
        while (possiblyAsset.isPresent()) {
            iri = base + "-" + i++;
            possiblyAsset = getAsset.apply(iri);
        }
        return iri;
    }
}
