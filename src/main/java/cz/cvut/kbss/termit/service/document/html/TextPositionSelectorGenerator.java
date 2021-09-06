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
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

import java.util.List;

/**
 * Generates a {@link TextPositionSelector} for the specified elements.
 * <p>
 * If there are multiple elements, the start position represent the character just before the first element's text
 * content and the end position is just after the last element's text content. It is assumed that there is no text
 * content in between the specified elements, so the end position is basically start position plus the length of text
 * content of the elements.
 * <p>
 * In order to be compatible with {@link cz.cvut.kbss.termit.model.selector.TextQuoteSelector} (as is required by the
 * specification), the generator uses only text content of the document, so any HTML/XML or other markup is ignored.
 */
class TextPositionSelectorGenerator implements SelectorGenerator {

    @Override
    public TextPositionSelector generateSelector(Element... elements) {
        assert elements.length > 0;
        final String textContent = extractExactText(elements);
        final TextPositionSelector selector = new TextPositionSelector();
        selector.setStart(resolveStartPosition(elements[0]));
        selector.setEnd(selector.getStart() + textContent.length());
        return selector;
    }

    private int resolveStartPosition(Element element) {
        final Elements ancestors = element.parents();
        Element previous = element;
        int counter = 0;
        for (Element parent : ancestors) {
            final List<Node> previousSiblings = parent.childNodes().subList(0, previous.siblingIndex());
            counter += extractNodeText(previousSiblings).length();
            previous = parent;
        }
        return counter;
    }
}
