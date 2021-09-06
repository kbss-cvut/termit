/**
 * TermIt
 * Copyright (C) 2019 Czech Technical University in Prague
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


import cz.cvut.kbss.termit.model.selector.TextPositionSelector;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TextPositionSelectorGeneratorTest {

    private static final String MATCH = "MATCH";

    private TextPositionSelectorGenerator sut;

    private Document document;

    @BeforeEach
    void setUp() {
        this.document = new Document("");
        this.sut = new TextPositionSelectorGenerator();
    }

    @Test
    void generateSelectorCreatesPositionSelectorForPlainContext() {
        final String prefix = "Prefix before the matching element ";
        final String suffix = " and suffix after the matching element.";
        document.html("<div>" + prefix + "<span id=\"elem\">" + MATCH + "</span>" + suffix + "</div>");
        final Element element = document.getElementById("elem");
        final TextPositionSelector result = sut.generateSelector(element);
        assertNotNull(result);
        assertEquals(prefix.length(), result.getStart().intValue());
        assertEquals(prefix.length() + MATCH.length(), result.getEnd().intValue());
    }

    @Test
    void generateSelectorGetsStartPositionFromDocumentBeginning() {
        final String prefix = "TitleParagraph containing the element with ";
        document.html("<div><h1>Title</h1><p>Paragraph containing the element with <span id=\"elem\">" + MATCH +
                "</span>.</p></div>");
        final Element element = document.getElementById("elem");
        final TextPositionSelector result = sut.generateSelector(element);
        assertNotNull(result);
        assertEquals(prefix.length(), result.getStart().intValue());
        assertEquals(prefix.length() + MATCH.length(), result.getEnd().intValue());
    }

    @Test
    void generateSelectorGeneratesPositionAndEndForMultipleElements() {
        final String prefix = "TitleFollowed by paragraph ending with ";
        final String match = "EXACT MATCH";
        document.html(
                "<div><h1>Title</h1><p>Followed by paragraph ending with <span>EXACT </span><span>MATCH</span> with full stop.</p></div>");
        final Elements elements = document.getElementsByTag("span");
        final TextPositionSelector result = sut.generateSelector(elements.toArray(new Element[0]));
        assertEquals(prefix.length(), result.getStart().intValue());
        assertEquals(prefix.length() + match.length(), result.getEnd().intValue());
    }

    @Test
    void generateSelectorSkipsComments() {
        final String prefix = "Prefix before the matching element ";
        final String suffix = " and suffix after the matching element.";
        document.html("<div>" + prefix + "<!-- Comment --><span id=\"elem\">" + MATCH + "</span>" + suffix + "</div>");
        final Element element = document.getElementById("elem");
        final TextPositionSelector result = sut.generateSelector(element);
        assertNotNull(result);
        assertEquals(prefix.length(), result.getStart().intValue());
        assertEquals(prefix.length() + MATCH.length(), result.getEnd().intValue());
    }
}