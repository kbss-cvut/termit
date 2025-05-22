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

import cz.cvut.kbss.termit.model.selector.Selector;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

/**
 * Generator of HTML/XML selectors.
 */
@FunctionalInterface
interface SelectorGenerator {

    /**
     * Generates selector for the specified elements' content.
     * <p>
     * The reason multiple elements are supported is because in case there are overlapping annotations, they are
     * represented by multiple elements using the <a href="https://en.wikipedia.org/wiki/Overlapping_markup#Joins">JOINS</a>
     * strategy.
     *
     * @param elements Elements to generate selector for. At least one must be provided
     * @return Selector for the text content of the specified elements
     */
    Selector generateSelector(Element... elements);

    /**
     * Extracts text content of the specified elements, joining them into one string.
     *
     * @param elements Elements to extract text from
     * @return Text content
     */
    default String extractExactText(Element[] elements) {
        final StringBuilder sb = new StringBuilder();
        for (Element element : elements) {
            sb.append(element.wholeText());
        }
        return sb.toString();
    }

    default StringBuilder extractNodeText(Iterable<Node> nodes) {
        final StringBuilder sb = new StringBuilder();
        for (Node node : nodes) {
            if (node instanceof TextNode textNode) {
                sb.append(textNode.getWholeText());
            } else if (node instanceof Element elementNode) {
                sb.append(elementNode.wholeText());
            }
        }
        return sb;
    }
}
