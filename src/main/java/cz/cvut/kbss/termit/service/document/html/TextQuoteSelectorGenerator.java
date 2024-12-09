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

import cz.cvut.kbss.termit.model.selector.TextQuoteSelector;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;

import java.util.List;
import java.util.Optional;

/**
 * Generates a {@link TextQuoteSelector} for the specified elements' content.
 * <p>
 * Note that if there are multiple elements specified for generation, it is expected that no text node is present
 * between the elements in the actual page. Thus, the exact match is created by concatenating the text content of all
 * the elements.
 */
class TextQuoteSelectorGenerator implements SelectorGenerator {

    private final int contextLength;

    TextQuoteSelectorGenerator(int contextLength) {
        this.contextLength = contextLength;
    }

    @Override
    public TextQuoteSelector generateSelector(Element... elements) {
        assert elements.length > 0;
        final TextQuoteSelector selector = new TextQuoteSelector(extractExactText(elements));
        extractPrefix(elements[0]).ifPresent(selector::setPrefix);
        extractSuffix(elements[elements.length - 1]).ifPresent(selector::setSuffix);
        return selector;
    }

    private Optional<String> extractPrefix(Element start) {
        Element current = start;
        Element previous = current;
        StringBuilder sb = new StringBuilder();
        while (current.hasParent()) {
            current = current.parent();
            assert current != null;
            final List<Node> previousSiblings = current.childNodes().subList(0, previous.siblingIndex());
            sb = extractNodeText(previousSiblings).append(sb);
            if (sb.length() >= contextLength) {
                break;
            }
            previous = current;
        }
        return !sb.isEmpty() ? Optional.of(sb.substring(Math.max(0, sb.length() - contextLength))) : Optional.empty();
    }

    private Optional<String> extractSuffix(Element end) {
        Element current = end;
        Element previous = current;
        StringBuilder sb = new StringBuilder();
        while (current.hasParent()) {
            current = current.parent();
            assert current != null;
            final List<Node> previousSiblings = current.childNodes()
                                                       .subList(previous.siblingIndex() + 1, current.childNodeSize());
            sb.append(extractNodeText(previousSiblings));
            if (sb.length() >= contextLength) {
                break;
            }
            previous = current;
        }
        return !sb.isEmpty() ? Optional.of(sb.substring(0, Math.min(sb.length(), contextLength))) : Optional.empty();
    }
}
