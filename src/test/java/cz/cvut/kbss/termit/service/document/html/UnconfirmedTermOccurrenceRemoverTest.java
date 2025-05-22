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
package cz.cvut.kbss.termit.service.document.html;

import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.util.TypeAwareByteArrayResource;
import cz.cvut.kbss.termit.util.TypeAwareResource;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnconfirmedTermOccurrenceRemoverTest {

    @Test
    void removeUnconfirmedOccurrencesReturnsContentWithoutSpansWithScoreThatIndicateOccurrenceIsUnconfirmed()
            throws Exception {
        TypeAwareResource input;
        try (final InputStream is = Environment.loadFile("data/rdfa-simple.html")) {
            input = new TypeAwareByteArrayResource(is.readAllBytes(), MediaType.TEXT_HTML_VALUE, ".html");
        }
        final TypeAwareResource result = new UnconfirmedTermOccurrenceRemover().removeUnconfirmedOccurrences(input);
        final Document doc = Jsoup.parse(result.getInputStream(), StandardCharsets.UTF_8.name(), "");
        assertTrue(doc.select("span[score]").isEmpty());
    }

    @Test
    void removeUnconfirmedOccurrencesPreservesSpansWithoutScoreRepresentingConfirmedOccurrences() throws Exception {
        TypeAwareResource input;
        try (final InputStream is = Environment.loadFile("data/rdfa-simple-no-score.html")) {
            input = new TypeAwareByteArrayResource(is.readAllBytes(), MediaType.TEXT_HTML_VALUE, ".html");
        }
        final TypeAwareResource result = new UnconfirmedTermOccurrenceRemover().removeUnconfirmedOccurrences(input);
        final Document doc = Jsoup.parse(result.getInputStream(), StandardCharsets.UTF_8.name(), "");
        assertFalse(
                doc.select("span[resource='http://onto.fel.cvut.cz/ontologies/mpp/domains/uzemni-plan']").isEmpty());
    }
}
