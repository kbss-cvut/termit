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

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.termit.environment.Generator;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.DynamicModelFactory;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.model.util.Values;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.eclipse.rdf4j.model.vocabulary.SKOS;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.locationtech.jts.util.Assert;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class UtilsTest {

    private static final String BASE = "BASE";

    @Test
    public void getUniqueIriFromBaseReturnsBaseIfCheckFails() {
        Assert.equals(BASE, Utils.getUniqueIriFromBase(BASE, (iri) -> Optional.empty()));
    }

    @Test
    public void getUniqueIriFromBaseReturnsBaseIfCheckSucceeds() {
        final Function<String, Optional<Object>> f = Mockito.mock(Function.class);
        when(f.apply(BASE)).thenReturn(Optional.of(new Object()));
        when(f.apply(BASE + "-0")).thenReturn(Optional.empty());
        Assert.equals(BASE + "-0", Utils.getUniqueIriFromBase(BASE, f));
    }

    @Test
    public void getVocabularyIriReturnsCorrectVocabularyIriIfAllConceptsHaveTheSameNamespace() {
        final Set<String> conceptIris = new HashSet<>();
        final String vocabularyIri = "https://example.org";
        final String namespace = vocabularyIri + "/pojem";
        conceptIris.add(namespace + "A");
        conceptIris.add(namespace + "B");
        Assert.equals(vocabularyIri, Utils.getVocabularyIri(conceptIris, "/pojem"));
    }

    @Test
    public void getVocabularyIriReturnsCorrectVocabularyIriForTermItVocabularies() {
        final Set<String> conceptIris = new HashSet<>();
        final String vocabularyIri = "https://example.org";
        final String namespace = vocabularyIri + "/pojem";
        conceptIris.add(namespace + "A");
        Assert.equals(vocabularyIri, Utils.getVocabularyIri(conceptIris, "/pojem"));
    }

    @Test
    public void getVocabularyIriReturnsCorrectVocabularyIriForExternalSlashVocabularies() {
        final Set<String> conceptIris = new HashSet<>();
        final String vocabularyIri = "https://example.org";
        final String namespace = vocabularyIri + "/";
        conceptIris.add(namespace + "A");
        Assert.equals(vocabularyIri, Utils.getVocabularyIri(conceptIris, "/pojem"));
    }

    @Test
    public void getVocabularyIriReturnsCorrectVocabularyIriForExternalHashVocabularies() {
        final Set<String> conceptIris = new HashSet<>();
        final String vocabularyIri = "https://example.org";
        final String namespace = vocabularyIri + "#";
        conceptIris.add(namespace + "A");
        Assert.equals(vocabularyIri, Utils.getVocabularyIri(conceptIris, "/pojem"));
    }

    @Test
    public void getVocabularyIriThrowsExceptionIfNoConceptIsProvided() {
        Assertions.assertThrows(IllegalArgumentException.class,
                                () -> Utils.getVocabularyIri(Collections.emptySet(), "/pojem"));
    }

    @Test
    public void getVocabularyIriThrowsExceptionIfConceptsWithDifferentNamespacesAreProvided() {
        final Set<String> conceptIris = new HashSet<>();
        conceptIris.add("https://example.org/pojem/A");
        conceptIris.add("https://example2.org/pojem/B");
        Assertions.assertThrows(IllegalArgumentException.class, () -> Utils.getVocabularyIri(conceptIris, "/pojem"));
    }

    @Test
    public void changeIriChangesIriCorrectly() {
        String oldIri = "https://example.org/a";
        String newIri = "https://example.org/b";
        Model model = new DynamicModelFactory().createEmptyModel();
        model.add(Values.iri(oldIri), RDF.TYPE, SKOS.CONCEPT);

        Utils.changeIri(oldIri, newIri, model);

        Assert.equals(1L, getStatementCountWithSubject(model, null));
        Assert.equals(0L, getStatementCountWithSubject(model, Values.iri(oldIri)));
        Assert.equals(1L, getStatementCountWithSubject(model, Values.iri(newIri)));
    }

    @Test
    public void changeNamespaceChangesNamespaceCorrectly() {
        String oldIri = "https://example.org/a";
        String newNamespace = "https://example2.org/";
        Model model = new DynamicModelFactory().createEmptyModel();
        model.add(Values.iri(oldIri), RDF.TYPE, SKOS.CONCEPT);

        Utils.changeNamespace("https://example.org/", newNamespace, model);

        Assert.equals(1L, getStatementCountWithSubject(model, null));
        Assert.equals(0L, getStatementCountWithSubject(model, Values.iri(oldIri)));
        Assert.equals(1L, getStatementCountWithSubject(model, Values.iri(newNamespace + "a")));
    }

    private long getStatementCountWithSubject(Model model, IRI subject) {
        return model.filter(subject, null, null).size();
    }

    @ParameterizedTest
    @MethodSource("collectionGenerator")
    void joinCollectionsJoinsCollections(Collection<String> cOne, Collection<String> cTwo,
                                         Collection<String> expected) {
        assertEquals(expected, Utils.joinCollections(cOne, cTwo));
    }

    static Stream<Arguments> collectionGenerator() {
        return Stream.of(
                Arguments.of(Collections.singleton("a"), Collections.singletonList("b"), Arrays.asList("a", "b")),
                Arguments.of(Collections.emptySet(), Collections.singleton("b"), Collections.singletonList("b")),
                Arguments.of(null, null, Collections.emptyList()),
                Arguments.of(null, Collections.singletonList("a"), Collections.singletonList("a"))
        );
    }

    @Test
    public void getLanguageTagsPerPropertiesReturnsCorrectLanguageTags() {
        final Model model = new LinkedHashModel();
        final ValueFactory f = SimpleValueFactory.getInstance();
        final String namespace = "https://example.org/";
        final String p1 = namespace + "p1";
        final String p2 = namespace + "p2";
        final IRI iriA = f.createIRI(namespace, "a");
        final IRI iriP1 = f.createIRI(p1);
        final IRI iriP2 = f.createIRI(p2);
        model.add(iriA, iriP1, f.createLiteral("a label cs", "cs"));
        model.add(iriA, iriP2, f.createLiteral("a label"));

        final Set<String> expected = Stream.of("cs", "").collect(Collectors.toSet());
        final Set<String> actual = Utils.getLanguageTagsPerProperties(model,
                                                                      Stream.of(p1, p2).collect(Collectors.toSet()));

        assertEquals(expected, actual);
    }

    @Test
    void trimReturnsEmptyStringForNullInput() {
        assertEquals("", Utils.trim(null));
    }

    @Test
    void trimReturnsTrimmedInputStringForNonNullInput() {
        final String input = "aaa bbb   ";
        assertEquals(input.trim(), Utils.trim(input));
    }

    @Test
    public void htmlToPlainTextExtractsTextFromHtmlString() {
        final String html = "<p>This is regular <b>text</b> mixed <i>with</i> <a href=\"www.inbas.cz\">HTML</a> tags.</p>";
        final String text = "This is regular text mixed with HTML tags.";

        assertThat(Utils.htmlToPlainText(html), containsString(text));
    }

    @Test
    void markdownToPlainTextExtractsTextFromMarkdownString() {
        final String markdown = "# This is a headline\n" +
                "**This is bold text**, _this is italics_";
        final String text = "This is a headline\n\nThis is bold text, this is italics";

        assertThat(Utils.markdownToPlainText(markdown), containsString(text));
    }

    @Test
    void markdownToPlainTextReturnsArgumentWhenItDoesNotContainMarkup() {
        final String text = "This is text without any markup.\n" +
                "It uses just newline";
        assertEquals(text, Utils.markdownToPlainText(text));
    }

    @Test
    void htmlToPlaintextReturnsArgumentWhenItDoesNotContainMarkup() {
        final String text = "This is text without any markup.\n" +
                "It uses just newline";
        assertEquals(text, Utils.htmlToPlainText(text));
    }

    @Test
    void pruneBlankTranslationsRemovesTranslationsThatAreBlankStrings() {
        final MultilingualString str = MultilingualString.create("test", "en");
        str.set("cs", "");
        str.set("de", "   ");
        Utils.pruneBlankTranslations(str);
        assertEquals(1, str.getValue().size());
        assertEquals("test", str.get("en"));
    }

    @Test
    void resolveTranslationsExtractsPropertyValuesWithLanguageTagsFromSpecifiedModel() {
        final Model model = new LinkedHashModel();
        final ValueFactory vf = SimpleValueFactory.getInstance();
        final IRI subject = vf.createIRI(Generator.generateUriString());
        final IRI property = RDFS.LABEL;
        final MultilingualString expected = MultilingualString.create("English", "en").set("cs", "Česky");
        expected.getValue().forEach((lang, v) -> model.add(subject, property, vf.createLiteral(v, lang)));

        assertEquals(expected, Utils.resolveTranslations(subject, property, model));
    }

    @Test
    void resolveTranslationsSkipsLanguageLessAndNonLiteralValues() {
        final Model model = new LinkedHashModel();
        final ValueFactory vf = SimpleValueFactory.getInstance();
        final IRI subject = vf.createIRI(Generator.generateUriString());
        final IRI property = RDFS.LABEL;
        model.add(subject, property, vf.createLiteral("Language-less string"));
        model.add(subject, property, vf.createIRI(Generator.generateUriString()));

        final MultilingualString result = Utils.resolveTranslations(subject, property, model);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
