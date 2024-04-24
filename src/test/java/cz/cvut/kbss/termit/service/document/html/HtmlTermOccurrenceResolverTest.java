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

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.assignment.TermOccurrence;
import cz.cvut.kbss.termit.model.resource.Document;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.service.document.DocumentManager;
import cz.cvut.kbss.termit.service.repository.TermRepositoryService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;

import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.Matchers.endsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private HtmlSelectorGenerators selectorGenerators = new HtmlSelectorGenerators();

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
        final Document document = new Document();
        document.setLabel("testDocument");
        document.setUri(Generator.generateUri());
        final File file = new File();
        file.setLabel("test");
        file.setDocument(document);
        document.addFile(file);
        when(documentManager.getContentType(file)).thenReturn(Optional.of(MediaType.TEXT_HTML_VALUE));
        assertTrue(sut.supports(file));
    }

    @Test
    void findTermOccurrencesExtractsAlsoScoreFromRdfa() {
        when(termService.exists(TERM_URI)).thenReturn(true);
        final File file = initFile();
        final InputStream is = cz.cvut.kbss.termit.environment.Environment.loadFile("data/rdfa-simple.html");
        sut.parseContent(is, file);
        final List<TermOccurrence> result = sut.findTermOccurrences();
        result.forEach(to -> {
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
        final List<TermOccurrence> result = sut.findTermOccurrences();
        result.forEach(to -> assertNull(to.getScore()));
    }

    @Test
    void findTermOccurrencesHandlesInvalidScoreInRdfa() {
        when(termService.exists(TERM_URI)).thenReturn(true);
        final File file = initFile();
        final InputStream is = cz.cvut.kbss.termit.environment.Environment
                .loadFile("data/rdfa-simple-invalid-score.html");
        sut.parseContent(is, file);
        final List<TermOccurrence> result = sut.findTermOccurrences();
        result.forEach(to -> assertNull(to.getScore()));
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
        final List<TermOccurrence> result = sut.findTermOccurrences();
        assertEquals(1, result.size());
        assertThat(result.get(0).getUri().toString(), startsWith(file.getUri() + "/" + TermOccurrence.CONTEXT_SUFFIX));
        assertThat(result.get(0).getUri().toString(), endsWith("1"));
    }

    @Test
    void findTermOccurrencesMarksOccurrencesAsSuggested() {
        when(termService.exists(TERM_URI)).thenReturn(true);
        final File file = initFile();
        final InputStream is = cz.cvut.kbss.termit.environment.Environment.loadFile("data/rdfa-simple.html");
        sut.parseContent(is, file);
        final List<TermOccurrence> result = sut.findTermOccurrences();
        result.forEach(to -> assertThat(to.getTypes(), hasItem(Vocabulary.s_c_navrzeny_vyskyt_termu)));
    }
}
