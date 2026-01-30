/*
 * TermIt
 * Copyright (C) 2025 Czech Technical University in Prague
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.util;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.termit.exception.ResourceNotFoundException;
import cz.cvut.kbss.termit.exception.TermItException;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Literal;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.util.Statements;
import org.eclipse.rdf4j.model.util.Values;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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
     * @param queryFilePath Path to the query file within the {@link Constants#QUERY_DIRECTORY}
     * @return Query string read from the file
     */
    public static String loadQuery(String queryFilePath) {
        return loadClasspathResource(Constants.QUERY_DIRECTORY + "/" + queryFilePath);
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
     * Extracts the necessary vocabulary namespace from the specified concept identifiers.
     * <p>
     * It is assumed the vocabulary namespace is the same for all concepts.
     *
     * @param conceptUris set of concept IRIs
     * @return vocabulary namespace
     * @throws IllegalArgumentException if the namespace is not the same in all concept IRIs, there is no concept, or
     *                                  the concept IRI is not an absolute IRI.
     */
    public static String extractVocabularyNamespaceFromTermIris(final Set<String> conceptUris) {
        if (conceptUris.isEmpty()) {
            throw new IllegalArgumentException("No namespace candidate.");
        }
        final Iterator<String> i = conceptUris.iterator();
        final String conceptUri = i.next();
        final String namespace = extractNamespace(conceptUri);
        for (final String s : conceptUris) {
            if (!s.startsWith(namespace)) {
                throw new IllegalArgumentException(
                        "Not all Concept IRIs have the same namespace: " + s + " vs. " + namespace);
            }
        }
        return namespace;
    }

    private static String extractNamespace(String conceptUri) {
        final String separator;
        if (conceptUri.lastIndexOf("#") > 0) {
            separator = "#";
        } else if (conceptUri.lastIndexOf("/") > 0) {
            separator = "/";
        } else {
            throw new IllegalArgumentException("The IRI does not have a proper format: " + conceptUri);
        }
        return conceptUri.substring(0, conceptUri.lastIndexOf(separator) + 1);
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
     * Extracts translations of values of the specified property of the specified subject.
     *
     * @param subject  Subject whose property values to extract
     * @param property Property whose values to extract
     * @param model    Model containing data to process
     * @return {@code MultilingualString} instance containing all available property value translations
     */
    public static MultilingualString resolveTranslations(Resource subject, IRI property, Model model) {
        final MultilingualString s = new MultilingualString();
        model.filter(subject, property, null).forEach(st -> {
            if (st.getObject().isLiteral()) {
                ((Literal) st.getObject()).getLanguage()
                                          .ifPresent(lang -> s.set(lang, ((Literal) st.getObject()).getLabel()));
            }
        });
        return s;
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
     * Checks whether the specified string is blank.
     * <p>
     * {@code null} is considered blank, too.
     *
     * @param input Input to check
     * @return {@code true} if the specified string is blank or null, {@code false} otherwise
     */
    public static boolean isBlank(String input) {
        return input == null || input.isBlank();
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
        str.getValue().entrySet().removeIf(e -> e.getValue().isBlank());
    }

    /**
     * Converts the map into a string
     *
     * @return Empty string when the map is {@code null}, otherwise the String in format {@code {key=value, key=value}}
     */
    public static <A, B> String mapToString(Map<A, B> map) {
        if (map == null) {
            return "";
        }
        return map.keySet().stream()
                  .map(key -> key + "=" + map.get(key))
                  .collect(Collectors.joining(", ", "{", "}"));
    }

    /**
     * Checks whether the {@code development} profile is active.
     *
     * @param activeProfiles Array of active profiles
     * @return {@code true} if the {@code development} profile is active, {@code false} otherwise
     */
    public static boolean isDevelopmentProfile(String[] activeProfiles) {
        Objects.requireNonNull(activeProfiles);
        Arrays.sort(activeProfiles);
        return Arrays.binarySearch(activeProfiles, Constants.DEVELOPMENT_PROFILE) != -1;
    }

    /**
     * Returns the specified number of milliseconds converted to seconds and rounded to two decimal points.
     *
     * @param millis Milliseconds to convert and round
     * @return String representation of the specified millis converted to seconds and rounded
     */
    public static String millisToString(long millis) {
        final float seconds = (float) millis / 1000;
        final DecimalFormat df = new DecimalFormat("0.00");
        return df.format(seconds);
    }

    /**
     * Uses Apache Tika to resolve the content type of the specified file.
     *
     * @param file File to resolve the content type of
     * @return Content type of the file (e.g., "text/turtle")
     * @throws IOException If the file cannot be read
     */
    public static String resolveContentType(MultipartFile file) throws IOException {
        Objects.requireNonNull(file);
        Metadata metadata = new Metadata();
        metadata.add(TikaCoreProperties.RESOURCE_NAME_KEY, file.getOriginalFilename());
        metadata.add(Metadata.CONTENT_TYPE, file.getContentType());
        return new Tika().detect(file.getInputStream(), metadata);
    }
}
