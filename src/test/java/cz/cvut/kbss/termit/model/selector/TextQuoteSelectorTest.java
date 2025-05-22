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
package cz.cvut.kbss.termit.model.selector;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TextQuoteSelectorTest {

    @Test
    void twoSelectorsWithSameExactMatchAreEqual() {
        final TextQuoteSelector sOne = new TextQuoteSelector("test value");
        final TextQuoteSelector sTwo = new TextQuoteSelector("test value");
        assertEquals(sOne, sTwo);
        assertEquals(sOne.hashCode(), sTwo.hashCode());
    }

    @Test
    void twoSelectorsWithSameExactMatchAndPrefixAreEqual() {
        final TextQuoteSelector sOne = new TextQuoteSelector("test value");
        sOne.setPrefix("Lorem ipsum dolor sit amet");
        final TextQuoteSelector sTwo = new TextQuoteSelector("test value");
        sTwo.setPrefix("Lorem ipsum dolor sit amet");
        assertEquals(sOne, sTwo);
        assertEquals(sOne.hashCode(), sTwo.hashCode());
    }

    @Test
    void twoSelectorsWithSameExactMatchAndSuffixAreEqual() {
        final TextQuoteSelector sOne = new TextQuoteSelector("test value");
        sOne.setSuffix("Lorem ipsum dolor sit amet");
        final TextQuoteSelector sTwo = new TextQuoteSelector("test value");
        sTwo.setSuffix("Lorem ipsum dolor sit amet");
        assertEquals(sOne, sTwo);
        assertEquals(sOne.hashCode(), sTwo.hashCode());
    }

    @Test
    void twoSelectorsWithSameExactMatchPrefixAndSuffixAreEqual() {
        final TextQuoteSelector sOne = new TextQuoteSelector("test value");
        sOne.setPrefix("Lorem ipsum dolor sit amet");
        sOne.setSuffix("Nulla non arcu lacinia neque faucibus fringilla.");
        final TextQuoteSelector sTwo = new TextQuoteSelector("test value");
        sTwo.setPrefix("Lorem ipsum dolor sit amet");
        sTwo.setSuffix("Nulla non arcu lacinia neque faucibus fringilla.");
        assertEquals(sOne, sTwo);
        assertEquals(sOne.hashCode(), sTwo.hashCode());
    }

    @Test
    void twoSelectorsWithDifferentPrefixAreNotEqual() {
        final TextQuoteSelector sOne = new TextQuoteSelector("test value");
        sOne.setPrefix("Lorem ipsum dolor sit amet changed");
        sOne.setSuffix("Nulla non arcu lacinia neque faucibus fringilla.");
        final TextQuoteSelector sTwo = new TextQuoteSelector("test value");
        sTwo.setPrefix("Lorem ipsum dolor sit amet");
        sTwo.setSuffix("Nulla non arcu lacinia neque faucibus fringilla.");
        assertNotEquals(sOne, sTwo);
    }
}