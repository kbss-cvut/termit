/*
 * TermIt
 * Copyright (C) 2023 Czech Technical University in Prague
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
package cz.cvut.kbss.termit.service.document.html;

import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.assignment.TermOccurrence;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.model.selector.Selector;
import cz.cvut.kbss.termit.model.selector.TextPositionSelector;
import cz.cvut.kbss.termit.model.selector.TextQuoteSelector;
import cz.cvut.kbss.termit.service.document.DocumentManager;
import cz.cvut.kbss.termit.service.repository.TermRepositoryService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HtmlTermOccurrenceResolverTest {

    private static final URI TERM_URI = URI.create("http://onto.fel.cvut.cz/ontologies/mpp/domains/uzemni-plan");

    @Mock
    private TermRepositoryService termService;

    @SuppressWarnings("unused")
    @Spy
    private Configuration config = new Configuration();

    @SuppressWarnings("unused")
    @Spy
    private HtmlSelectorGenerators selectorGenerators = new HtmlSelectorGenerators(config);

    @Mock
    private DocumentManager documentManager;

    @InjectMocks
    private HtmlTermOccurrenceResolver sut;

    @Test
    void supportsReturnsTrueForFileWithHtmlLabelExtension() {
        final File file = new File();
        file.setLabel("rdfa-simple.html");
        assertTrue(sut.supports(file));
    }

    @Test
    void supportsReturnsTrueForFileWithHtmLabelExtension() {
        final File file = new File();
        file.setLabel("rdfa-simple.htm");
        assertTrue(sut.supports(file));
    }

    @Test
    void supportsReturnsTrueForHtmlFileWithoutExtension() {
        final File file = new File();
        file.setLabel("test");
        when(documentManager.getContentType(file)).thenReturn(Optional.of(MediaType.TEXT_HTML_VALUE));
        assertTrue(sut.supports(file));
    }

    @Test
    void findTermOccurrencesExtractsAlsoScoreFromRdfa() {
        when(termService.exists(TERM_URI)).thenReturn(true);
        final File file = initFile();
        final InputStream is = cz.cvut.kbss.termit.environment.Environment.loadFile("data/rdfa-simple.html");
        sut.parseContent(is, file);
        sut.findTermOccurrences(to -> {
            assertNotNull(to.getScore());
            assertThat(to.getScore(), greaterThan(0.0));
        });
    }

    private static File initFile() {
        final File file = new File();
        file.setLabel("rdfa-simple.html");
        file.setUri(URI.create(Vocabulary.s_c_soubor + "/" + file.getLabel()));
        return file;
    }

    @Test
    void findTermOccurrencesHandlesRdfaWithoutScore() {
        when(termService.exists(TERM_URI)).thenReturn(true);
        final File file = initFile();
        final InputStream is = cz.cvut.kbss.termit.environment.Environment.loadFile("data/rdfa-simple-no-score.html");
        sut.parseContent(is, file);
        sut.findTermOccurrences(to -> assertNull(to.getScore()));
    }

    @Test
    void findTermOccurrencesHandlesInvalidScoreInRdfa() {
        when(termService.exists(TERM_URI)).thenReturn(true);
        final File file = initFile();
        final InputStream is = cz.cvut.kbss.termit.environment.Environment
                .loadFile("data/rdfa-simple-invalid-score.html");
        sut.parseContent(is, file);
        sut.findTermOccurrences(to -> assertNull(to.getScore()));
    }

    @Test
    void supportsReturnsTrueForTerm() {
        assertTrue(sut.supports(Generator.generateTermWithId()));
    }

    @Test
    void findTermOccurrencesGeneratesOccurrenceUriBasedOnAnnotationAbout() {
        when(termService.exists(TERM_URI)).thenReturn(true);
        final File file = initFile();
        final InputStream is = cz.cvut.kbss.termit.environment.Environment.loadFile("data/rdfa-simple.html");
        sut.parseContent(is, file);
        AtomicInteger resultSize = new AtomicInteger(0);
        sut.findTermOccurrences(to -> {
            resultSize.incrementAndGet();
            assertThat(to.getUri().toString(), startsWith(file.getUri() + "/" + TermOccurrence.CONTEXT_SUFFIX));
            assertThat(to.getUri().toString(), endsWith("1"));
        });
        assertEquals(1, resultSize.get());

    }

    @Test
    void findTermOccurrencesMarksOccurrencesAsSuggested() {
        when(termService.exists(TERM_URI)).thenReturn(true);
        final File file = initFile();
        final InputStream is = cz.cvut.kbss.termit.environment.Environment.loadFile("data/rdfa-simple.html");
        sut.parseContent(is, file);
        sut.findTermOccurrences(to -> assertThat(to.getTypes(), hasItem(Vocabulary.s_c_navrzeny_vyskyt_termu)));
    }

    @Test
    void findTermOccurrencesSetsFoundOccurrencesAsApprovedWhenCorrespondingExistingOccurrenceWasApproved()
            throws Exception {
        when(termService.exists(TERM_URI)).thenReturn(true);
        final File file = initFile();
        final TermOccurrence existing = Generator.generateTermOccurrence(new Term(TERM_URI), file, false);
        final Selector quoteSelector = new TextQuoteSelector("Územní plán", "RDFa simple", "hlavního města Prahy.");
        final Selector posSelector = new TextPositionSelector(29, 40);
        existing.getTarget().setSelectors(Set.of(quoteSelector, posSelector));
        final InputStream is = cz.cvut.kbss.termit.environment.Environment.loadFile("data/rdfa-simple.html");
        sut.parseContent(is, file);
        sut.setExistingOccurrences(List.of(existing));

        AtomicInteger resultSize = new AtomicInteger(0);
        sut.findTermOccurrences(to -> {
            resultSize.incrementAndGet();
            assertThat(to.getTypes(), not(hasItem(Vocabulary.s_c_navrzeny_vyskyt_termu)));
        });
        assertEquals(1, resultSize.get());
        final Document document = Jsoup.parse(sut.getContent(), StandardCharsets.UTF_8.name(), "");
        final Elements annotations = document.select("span[about]");
        assertEquals(1, annotations.size());
        assertFalse(annotations.get(0).hasAttr("score"));
    }

    @Test
    void findTermOccurrencesReusesExistingApprovedOccurrencesThatAreNotPresentInAnnotatedContent() throws Exception {
        when(termService.exists(TERM_URI)).thenReturn(true);
        final File file = initFile();
        final TermOccurrence existing = Generator.generateTermOccurrence(new Term(TERM_URI), file, false);
        existing.setUri(URI.create(Vocabulary.s_c_vyskyt_termu + "/r2d2"));
        final Selector quoteSelector = new TextQuoteSelector("Prahy", " hlavního města ", ".");
        final Selector posSelector = new TextPositionSelector(57, 62);
        existing.getTarget().setSelectors(Set.of(quoteSelector, posSelector));
        final InputStream is = cz.cvut.kbss.termit.environment.Environment.loadFile("data/rdfa-simple.html");
        sut.parseContent(is, file);
        sut.setExistingOccurrences(List.of(existing));

        final List<TermOccurrence> result = new ArrayList<>();
        sut.findTermOccurrences(result::add);
        final Optional<TermOccurrence> matchingExisting = result.stream()
                                                                .filter(to -> Stream.of(quoteSelector, posSelector)
                                                                                    .anyMatch(sel -> to.getTarget()
                                                                                                       .getSelectors()
                                                                                                       .contains(
                                                                                                               sel)))
                                                                .findFirst();
        assertTrue(matchingExisting.isPresent());
        final Document resultDoc = Jsoup.parse(sut.getContent(), StandardCharsets.UTF_8.name(), "");
        final Elements addedAnnotation = resultDoc.select(
                "span[about=" + matchingExisting.get().resolveElementAbout() + "]");
        assertFalse(addedAnnotation.isEmpty());
    }

    @Test
    void findTermOccurrencesReusesExistingApprovedOccurrencesButCreatesTheirCopies() {
        when(termService.exists(TERM_URI)).thenReturn(true);
        final File file = initFile();
        final TermOccurrence existing = Generator.generateTermOccurrence(new Term(TERM_URI), file, false);
        existing.setUri(URI.create(Vocabulary.s_c_vyskyt_termu + "/r2d2"));
        final Selector quoteSelector = new TextQuoteSelector("Prahy", " hlavního města ", ".");
        final Selector posSelector = new TextPositionSelector(57, 62);
        existing.getTarget().setSelectors(Set.of(quoteSelector, posSelector));
        final InputStream is = cz.cvut.kbss.termit.environment.Environment.loadFile("data/rdfa-simple.html");
        sut.parseContent(is, file);
        sut.setExistingOccurrences(List.of(existing));

        final List<TermOccurrence> result = new ArrayList<>();
        sut.findTermOccurrences(result::add);
        final Optional<TermOccurrence> existingCopy = result.stream().filter(to -> Stream.of(quoteSelector, posSelector)
                                                                                         .anyMatch(sel -> to.getTarget()
                                                                                                            .getSelectors()
                                                                                                            .contains(
                                                                                                                    sel)))
                                                            .findFirst();
        assertTrue(existingCopy.isPresent());
        assertNotEquals(existing.getUri(), existingCopy.get().getUri());
    }

    @Test
    void findTermOccurrencesReusesExistingApprovedOccurrenceElementInContent() throws Exception {
        when(termService.exists(TERM_URI)).thenReturn(true);
        final File file = initFile();
        final TermOccurrence existing = Generator.generateTermOccurrence(new Term(TERM_URI), file, false);
        existing.setUri(URI.create(Vocabulary.s_c_vyskyt_termu + "/r2d2"));
        final Selector quoteSelector = new TextQuoteSelector("Územní plán", "", " hlavního města Prahy.");
        final Selector posSelector = new TextPositionSelector(29, 40);
        existing.getTarget().setSelectors(Set.of(quoteSelector, posSelector));
        InputStream is = cz.cvut.kbss.termit.environment.Environment.loadFile("data/rdfa-simple.html");
        final Document doc = Jsoup.parse(is, StandardCharsets.UTF_8.name(), "");
        doc.select("span[about]").removeAttr("score");
        is = new ByteArrayInputStream(doc.toString().getBytes(StandardCharsets.UTF_8));
        sut.parseContent(is, file);
        sut.setExistingOccurrences(List.of(existing));

        final List<TermOccurrence> result = new ArrayList<>();
        sut.findTermOccurrences(result::add);
        assertEquals(1, result.size());
        final Document resultDoc = Jsoup.parse(sut.getContent(), StandardCharsets.UTF_8.name(), "");
        assertEquals(1, resultDoc.select("span[about]").size());
    }

    @Test
    void findTermOccurrencesReplacesTwoSuggestedOccurrencesWithSingleExistingApprovedOccurrence() throws Exception {
        when(termService.exists(TERM_URI)).thenReturn(true);
        when(termService.exists(URI.create("http://onto.fel.cvut.cz/ontologies/mpp/domains/plan"))).thenReturn(true);
        final File file = initFile();
        final TermOccurrence existing = Generator.generateTermOccurrence(new Term(TERM_URI), file, false);
        existing.setUri(URI.create(Vocabulary.s_c_vyskyt_termu + "/r2d2"));
        final Selector quoteSelector = new TextQuoteSelector("Územní plán", "", " hlavního města Prahy.");
        final Selector posSelector = new TextPositionSelector(29, 40);
        existing.getTarget().setSelectors(Set.of(quoteSelector, posSelector));
        InputStream is = cz.cvut.kbss.termit.environment.Environment.loadFile("data/rdfa-simple-two-occurrences.html");
        sut.parseContent(is, file);
        sut.setExistingOccurrences(List.of(existing));

        final List<TermOccurrence> result = new ArrayList<>();
        sut.findTermOccurrences(result::add);
        final Document resultDoc = Jsoup.parse(sut.getContent(), StandardCharsets.UTF_8.name(), "");
        assertEquals(1, resultDoc.select("span[about]").size());
        assertTrue(result.stream()
                         .anyMatch(r -> !r.isSuggested() && r.getTarget().getSelectors().contains(quoteSelector)));
    }

    @Test
    void findTermOccurrencesResolvesExistingApprovedOccurrenceInContentWithTwoOccurrencesOfSameTermInOneParent() throws Exception {
        config.getTextAnalysis().setTextQuoteSelectorContextLength(8);
        this.selectorGenerators = new HtmlSelectorGenerators(config);
        this.sut = new HtmlTermOccurrenceResolver(termService, selectorGenerators, documentManager, config);
        final File file = initFile();
        final TermOccurrence existing = Generator.generateTermOccurrence(new Term(TERM_URI), file, false);
        existing.setUri(URI.create(Vocabulary.s_c_vyskyt_termu + "/r2d2"));
        final Selector quoteSelector = new TextQuoteSelector("Územní plán", "stovací ", " hlavníh");
        final Selector posSelector = new TextPositionSelector(22, 33);
        existing.getTarget().setSelectors(Set.of(quoteSelector, posSelector));
        final InputStream is = Environment.loadFile("data/rdfa-two-occurrences-one-parent.html");
        sut.parseContent(is, file);
        sut.setExistingOccurrences(List.of(existing));

        final List<TermOccurrence> result = new ArrayList<>();
        sut.findTermOccurrences(result::add);
        assertTrue(result.stream().filter(to -> !to.isSuggested()).noneMatch(to -> Objects.equals(to.getUri(), existing.getUri())));
        final Document resultDoc = Jsoup.parse(sut.getContent(), StandardCharsets.UTF_8.name(), "");
        final Element elem = resultDoc.select("span[about]").first();
        assertNotNull(elem);
        assertNotNull(elem.parent());
        final Node previousSibling = elem.parent().previousSibling();
        assertNotNull(previousSibling);
        assertInstanceOf(TextNode.class, previousSibling);
        assertThat(((TextNode) previousSibling).text(), containsString("Testovací"));
    }
}
