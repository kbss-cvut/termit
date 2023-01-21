package cz.cvut.kbss.termit.util;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.termit.exception.ResourceNotFoundException;
import cz.cvut.kbss.termit.exception.TermItException;
import org.eclipse.rdf4j.model.*;
import org.eclipse.rdf4j.model.util.Statements;
import org.eclipse.rdf4j.model.util.Values;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Utils {

    /**
     * Email validation regexp by OWASP.
     */
    private static final String EMAIL_REGEXP = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,15}$";

    private Utils() {
        throw new AssertionError();
    }

    /**
     * Returns an empty set if the specified set is {@code null}. Otherwise, the collection itself is returned.
     *
     * @param set The collection to check
     * @return Non-null collection
     */
    public static <T> Set<T> emptyIfNull(Set<T> set) {
        return set == null ? Collections.emptySet() : set;
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
        return loadClasspathResource(Constants.QUERY_DIRECTORY + "/" + queryFileName);
    }

    /**
     * Loads the content of a text file from the classpath.
     * <p>
     * That is, this method resolves the specified path w.r.t. the application classpath.
     *
     * @param path Path to the file to load
     * @return Content of the file as a string
     */
    public static String loadClasspathResource(String path) {
        final InputStream is = Utils.class.getClassLoader().getResourceAsStream(path);
        if (is == null) {
            throw new ResourceNotFoundException("Initialization exception. Classpath resource not found in " + path);
        }
        try (final BufferedReader in = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return in.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new TermItException("Initialization exception. Unable to load classpath resource!", e);
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
        changeIrisByFunction(originalIri::equals, oIri -> newIri, model);
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
                statementsToRemove
                        .add(Statements.statement(s.getSubject(), s.getPredicate(), s.getObject(), s.getContext()));
            }
        });
        model.removeAll(statementsToRemove);
        model.addAll(statementsToAdd);
    }

    /**
     * Extracts the necessary vocabulary IRI from the namespace of the current concepts.
     *
     * @param conceptUris   set of concept IRIs
     * @param termSeparator separator between a term local name and vocabulary IRI
     * @return IRI of the vocabulary
     * @throws IllegalArgumentException if the namespace is not unique in concept IRIs, there is no concept, or the
     *                                  concept IRI is not an absolute IRI.
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
                throw new IllegalArgumentException(
                        "Not all Concept IRIs have the same namespace: " + conceptUri + " vs. " + namespace);
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
     * @return Generated identifier
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

    /**
     * Joins the contents of the specified collections into one.
     * <p>
     * Note that this method performs no uniqueness checks or sorting. It only handles possible {@code null} arguments.
     *
     * @param collections Collections to join, can be null
     * @param <T>         Type of the collection elements
     * @return Collection containing elements from all the specified collections
     */
    @SafeVarargs
    public static <T> Collection<T> joinCollections(Collection<T>... collections) {
        final List<T> result = new ArrayList<>();
        for (Collection<T> col : collections) {
            if (col != null) {
                result.addAll(col);
            }
        }
        return result;
    }

    /**
     * Lists all distinct language tags that occur in literals which are objects of the given properties.
     *
     * @param model the model to look into
     * @param props set of property IRIs
     * @return set of language tags
     */
    public static Set<String> getLanguageTagsPerProperties(final Model model, final Set<String> props) {
        return model.stream()
                    .filter(statement -> props.contains(statement.getPredicate().stringValue()))
                    .filter(statement -> statement.getObject().isLiteral())
                    .map(statement -> ((Literal) statement.getObject()).getLanguage().orElse(""))
                    .collect(Collectors.toSet());
    }

    /**
     * Returns a timestamp representing the current time instant, truncated to milliseconds for simpler manipulation.
     *
     * @return Current instant truncated to millis
     */
    public static Instant timestamp() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    /**
     * Trims the specified input string, if it is not {@code null}.
     * <p>
     * If it is, returns an empty string.
     *
     * @param input Input string, possibly {@code null}
     * @return Trimmed input string, not {@code null}
     */
    public static String trim(String input) {
        return input != null ? input.trim() : "";
    }

    /**
     * Wraps the specified URI in {@code <} and {@code >} to provide constant {@link Object#toString()} behavior.
     *
     * @param uri URI to stringify
     * @return URI wrapped in less that and more than signs
     */
    public static String uriToString(URI uri) {
        return "<" + uri + ">";
    }

    /**
     * Checks if the specified string is a valid email address.
     *
     * @param str String to validate
     * @return {@code true} if the specified string is a valid email, {@code false} otherwise
     */
    public static boolean isValidEmail(String str) {
        return str != null && Pattern.compile(EMAIL_REGEXP).matcher(str).matches();
    }

    /**
     * Converts the specified HTML to plain text by removing all HTML tags and keeping only the text.
     *
     * @param html The HTML to convert
     * @return Text content of the input string
     */
    public static String htmlToPlainText(String html) {
        Objects.requireNonNull(html);
        final Document doc = Jsoup.parse(html);
        final Document.OutputSettings outputSettings = new Document.OutputSettings();
        outputSettings.prettyPrint(false);
        doc.outputSettings(outputSettings);
        doc.select("br").before("\\n");
        doc.select("p").before("\\n");
        final String str = doc.html().replaceAll("\\\\n", "\n");
        return Jsoup.clean(str, "", Safelist.none(), outputSettings).trim();
    }

    /**
     * Converts the specified Markdown to plain text by removing all Markdown markup and keeping only the text.
     *
     * @param markdown The Markdown content to convert
     * @return Text content of the input string
     */
    public static String markdownToPlainText(String markdown) {
        if (markdown == null) {
            return null;
        }
        final Parser parser = Parser.builder().build();
        final Node document = parser.parse(markdown);
        final HtmlRenderer renderer = HtmlRenderer.builder().build();
        final String html = renderer.render(document);
        return htmlToPlainText(html);
    }

    /**
     * Removes blank translations from the specified {@link MultilingualString}.
     * <p>
     * That is, deletes records in languages where the value is a blank string.
     *
     * @param str Multilingual string to prune, possibly {@code null}
     */
    public static void pruneBlankTranslations(MultilingualString str) {
        if (str == null) {
            return;
        }
        // TODO Rewrite to entrySet.removeIf once JOPA MultilingualString.getValue does not return unmodifiable Map
        final Set<String> langsToRemove = str.getValue().entrySet().stream().filter(e -> e.getValue().isBlank())
                                             .map(Map.Entry::getKey).collect(Collectors.toSet());
        langsToRemove.forEach(str::remove);
    }
}
