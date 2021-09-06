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

import cz.cvut.kbss.termit.model.selector.Selector;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Combines generators of selectors for HTML/XML elements.
 * <p>
 * Currently, {@link TextQuoteSelectorGenerator} and {@link TextPositionSelectorGenerator} are used.
 *
 * @see TextQuoteSelectorGenerator
 * @see TextPositionSelectorGenerator
 */
@Service
public class HtmlSelectorGenerators {

    private final List<SelectorGenerator> generators = Arrays
            .asList(new TextQuoteSelectorGenerator(), new TextPositionSelectorGenerator());

    /**
     * Generates selectors for the specified HTML/XML elements.
     *
     * @param elements Elements to generate selectors for
     * @return Set of generated selectors
     */
    public Set<Selector> generateSelectors(Element... elements) {
        return generators.stream().map(g -> g.generateSelector(elements)).collect(Collectors.toSet());
    }
}
