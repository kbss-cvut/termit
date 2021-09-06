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

import cz.cvut.kbss.termit.model.selector.TextQuoteSelector;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static cz.cvut.kbss.termit.service.document.html.TextQuoteSelectorGenerator.CONTEXT_LENGTH;
import static org.junit.jupiter.api.Assertions.*;

class TextQuoteSelectorGeneratorTest {

    private TextQuoteSelectorGenerator sut;

    private Document document;

    @BeforeEach
    void setUp() {
        this.document = new Document("");
        this.sut = new TextQuoteSelectorGenerator();
    }

    @Test
    void generateSelectorExtractsPrefixAndSuffixFromElementSiblingTextNodes() {
        final String prefix = "This is a prefix for the exact value ";
        final String suffix = ". And this is the suffix. It started with a dot ending the previous sentence and continues here.";
        final String exact = "EXACT";
        document.html("<div>" + prefix + "<span id=\"elem\">" + exact + "</span>" + suffix + "</div>");
        final Element element = document.getElementById("elem");
        final TextQuoteSelector result = sut.generateSelector(element);
        assertNotNull(result);
        assertEquals(exact, result.getExactMatch());
        assertEquals(prefix.substring(Math.max(0, prefix.length() - CONTEXT_LENGTH)), result.getPrefix());
        assertEquals(suffix.substring(0, Math.min(suffix.length(), CONTEXT_LENGTH)), result.getSuffix());
    }

    @Test
    void generateSelectorExtractsShorterPrefixWhenThereIsNotEnoughText() {
        final String prefix = "Short prefix of ";
        final String suffix = ". And this is the suffix. It started with a dot ending the previous sentence and continues here.";
        final String exact = "EXACT";
        document.html("<div>" + prefix + "<span id=\"elem\">" + exact + "</span>" + suffix + "</div>");
        final Element element = document.getElementById("elem");
        final TextQuoteSelector result = sut.generateSelector(element);
        assertNotNull(result);
        assertEquals(exact, result.getExactMatch());
        assertEquals(prefix, result.getPrefix());
        assertEquals(suffix.substring(0, Math.min(suffix.length(), CONTEXT_LENGTH)), result.getSuffix());
    }

    @Test
    void generateSelectorExtractsShorterSuffixWhenThereIsNotEnoughText() {
        final String prefix = "This is a prefix for the exact value ";
        final String suffix = ". Here is suffix.";
        final String exact = "EXACT";
        document.html("<div>" + prefix + "<span id=\"elem\">" + exact + "</span>" + suffix + "</div>");
        final Element element = document.getElementById("elem");
        final TextQuoteSelector result = sut.generateSelector(element);
        assertNotNull(result);
        assertEquals(exact, result.getExactMatch());
        assertEquals(prefix.substring(Math.max(0, prefix.length() - CONTEXT_LENGTH)), result.getPrefix());
        assertEquals(suffix, result.getSuffix());
    }

    @Test
    void generateSelectorUsesTextContentOfMultipleSiblingsToFillPrefix() {
        final String prefix = "Prefix is now split with tags. ";
        final String suffix = ". And this is the suffix. It started with a dot ending the previous sentence and continues here.";
        final String exact = "EXACT";
        document.html("<div>Prefix is now <i>split</i> with tags. <span id=\"elem\">" + exact + "</span>" + suffix +
                "</div>");
        final Element element = document.getElementById("elem");
        final TextQuoteSelector result = sut.generateSelector(element);
        assertNotNull(result);
        assertEquals(exact, result.getExactMatch());
        assertEquals(prefix, result.getPrefix());
        assertEquals(suffix.substring(0, Math.min(suffix.length(), CONTEXT_LENGTH)), result.getSuffix());
    }

    @Test
    void generateSelectorUsesTextContentOfMultipleSiblingsToFillSuffix() {
        final String prefix = "Prefix is still plain text, ";
        final String suffix = " but suffix is split into multiple nodes and is long.";
        final String exact = "EXACT";
        document.html("<div>" + prefix + "<span id=\"elem\">" + exact +
                "</span> but <b>suffix is split</b> into multiple nodes and is long.</div>");
        final Element element = document.getElementById("elem");
        final TextQuoteSelector result = sut.generateSelector(element);
        assertNotNull(result);
        assertEquals(exact, result.getExactMatch());
        assertEquals(prefix.substring(Math.max(0, prefix.length() - CONTEXT_LENGTH)), result.getPrefix());
        assertEquals(suffix.substring(0, Math.min(suffix.length(), CONTEXT_LENGTH)), result.getSuffix());
    }

    @Test
    void generateSelectorUsesTextContentOfParentSiblingsToFillPrefix() {
        final String prefix = "TitleTitle will be part of prefix, ";
        final String suffix = ". And this is the suffix. It started with a dot ending the previous sentence and continues here.";
        final String exact = "EXACT";
        document.html(
                "<div><h1>Title</h1><p>Title will be part of prefix, <span id=\"elem\">" + exact + "</span>" + suffix +
                        "</p></div>");
        final Element element = document.getElementById("elem");
        final TextQuoteSelector result = sut.generateSelector(element);
        assertNotNull(result);
        assertEquals(exact, result.getExactMatch());
        assertEquals(prefix.substring(Math.max(0, prefix.length() - CONTEXT_LENGTH)), result.getPrefix());
        assertEquals(suffix.substring(0, Math.min(suffix.length(), CONTEXT_LENGTH)), result.getSuffix());
    }

    @Test
    void generateSelectorUsesTextContentOfParentSiblingsToFillSuffix() {
        final String prefix = "Title ";
        final String suffix = " FollowsSuffix is completely inside parent's sibling.";
        final String exact = "EXACT";
        document.html(
                "<div><h1>Title <span id=\"elem\">" + exact +
                        "</span> Follows</h1><p>Suffix is completely inside parent's sibling.</p></div>");
        final Element element = document.getElementById("elem");
        final TextQuoteSelector result = sut.generateSelector(element);
        assertNotNull(result);
        assertEquals(exact, result.getExactMatch());
        assertEquals(prefix.substring(Math.max(0, prefix.length() - CONTEXT_LENGTH)), result.getPrefix());
        assertEquals(suffix.substring(0, Math.min(suffix.length(), CONTEXT_LENGTH)), result.getSuffix());
    }

    @Test
    void generateSelectorLeavesEmptyPrefixWhenNoPreviousNodesExist() {
        final String suffix = " FollowsSuffix is completely inside parent's sibling.";
        final String exact = "EXACT";
        document.html(
                "<div><h1><span id=\"elem\">" + exact +
                        "</span> Follows</h1><p>Suffix is completely inside parent's sibling.</p></div>");
        final Element element = document.getElementById("elem");
        final TextQuoteSelector result = sut.generateSelector(element);
        assertNotNull(result);
        assertEquals(exact, result.getExactMatch());
        assertNull(result.getPrefix());
        assertEquals(suffix.substring(0, Math.min(suffix.length(), CONTEXT_LENGTH)), result.getSuffix());
    }

    @Test
    void generateSelectorLeavesEmptySuffixWhenNoFollowingNodesExist() {
        final String prefix = "TitleFollowed by paragraph ending with ";
        final String exact = "EXACT";
        document.html(
                "<div><h1>Title</h1><p>Followed by paragraph ending with <span id=\"elem\">" + exact +
                        "</span></p></div>");
        final Element element = document.getElementById("elem");
        final TextQuoteSelector result = sut.generateSelector(element);
        assertNotNull(result);
        assertEquals(exact, result.getExactMatch());
        assertEquals(prefix.substring(Math.max(0, prefix.length() - CONTEXT_LENGTH)), result.getPrefix());
        assertNull(result.getSuffix());
    }

    @Test
    void generateSelectorCreatesSelectorFromMultipleElements() {
        final String prefix = "TitleFollowed by paragraph ending with ";
        final String suffix = " with full stop.";
        final String exact = "EXACT MATCH";
        document.html(
                "<div><h1>Title</h1><p>Followed by paragraph ending with <span>EXACT </span><span>MATCH</span> with full stop.</p></div>");
        final Elements elements = document.getElementsByTag("span");
        final TextQuoteSelector result = sut.generateSelector(elements.toArray(new Element[0]));
        assertNotNull(result);
        assertEquals(exact, result.getExactMatch());
        assertEquals(prefix.substring(Math.max(0, prefix.length() - CONTEXT_LENGTH)), result.getPrefix());
        assertEquals(suffix.substring(0, Math.min(suffix.length(), CONTEXT_LENGTH)), result.getSuffix());
    }

    @Test
    void generateSelectorSkipsComments() {
        final String prefix = "Prefix";
        final String suffix = ". Suffix.";
        final String exact = "EXACT";
        document.html("<div>" + prefix + "<!-- comment --><span id=\"elem\">" + exact + "</span>" + suffix + "</div>");
        final Element element = document.getElementById("elem");
        final TextQuoteSelector result = sut.generateSelector(element);
        assertNotNull(result);
        assertEquals(exact, result.getExactMatch());
        assertEquals(prefix.substring(Math.max(0, prefix.length() - CONTEXT_LENGTH)), result.getPrefix());
        assertEquals(suffix.substring(0, Math.min(suffix.length(), CONTEXT_LENGTH)), result.getSuffix());
    }

    @Test
    void generateSelectorDoctypeDeclarationOfDocument() {
        final String exact = "EXACT";
        final String suffix = ". Suffix";
        document = Jsoup.parse("<!DOCTYPE html><html><head></head><body><div><span id=\"elem\">" + exact + "</span>" + suffix + "</div></body></html>");
        final Element element = document.getElementById("elem");
        final TextQuoteSelector result = sut.generateSelector(element);
        assertNotNull(result);
        assertEquals(exact, result.getExactMatch());
        assertEquals(suffix.substring(0, Math.min(suffix.length(), CONTEXT_LENGTH)), result.getSuffix());
    }
}
