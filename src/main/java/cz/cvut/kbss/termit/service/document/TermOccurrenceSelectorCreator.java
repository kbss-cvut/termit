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
package cz.cvut.kbss.termit.service.document;

import cz.cvut.kbss.termit.exception.SelectorGenerationException;
import cz.cvut.kbss.termit.exception.UnsupportedOperationException;
import cz.cvut.kbss.termit.model.assignment.DefinitionalOccurrenceTarget;
import cz.cvut.kbss.termit.model.assignment.FileOccurrenceTarget;
import cz.cvut.kbss.termit.model.assignment.OccurrenceTarget;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.model.resource.Resource;
import cz.cvut.kbss.termit.model.selector.Selector;
import cz.cvut.kbss.termit.service.business.ResourceService;
import cz.cvut.kbss.termit.service.document.html.HtmlSelectorGenerators;
import cz.cvut.kbss.termit.util.Constants;
import jakarta.annotation.Nonnull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;

/**
 * Creates selectors for a new term occurrence.
 */
@Component
public class TermOccurrenceSelectorCreator {

    private final HtmlSelectorGenerators selectorGenerators;

    private final DocumentManager documentManager;

    private final ResourceService resourceService;

    public TermOccurrenceSelectorCreator(HtmlSelectorGenerators selectorGenerators, DocumentManager documentManager,
                                         ResourceService resourceService) {
        this.selectorGenerators = selectorGenerators;
        this.documentManager = documentManager;
        this.resourceService = resourceService;
    }

    /**
     * Creates selectors for a term occurrence in the specified target represented by an HTML element with the specified
     * id.
     *
     * @param target       Asset in which the occurrence is to be found
     * @param elementAbout Value of the {@literal about} attribute of the occurrence element
     * @return Set of generated selectors for the occurrence element
     * @throws UnsupportedOperationException If the specified target is not supported
     * @throws SelectorGenerationException   If unable to generate selectors
     */
    public Set<Selector> createSelectors(@Nonnull OccurrenceTarget target, @Nonnull String elementAbout) {
        Objects.requireNonNull(target);
        Objects.requireNonNull(elementAbout);
        if (target instanceof DefinitionalOccurrenceTarget) {
            throw new UnsupportedOperationException("Cannot create selectors for a term definitional occurrence.");
        }
        final FileOccurrenceTarget ft = (FileOccurrenceTarget) target;
        final Document targetContent = loadTargetContent(ft);
        final Elements elements = targetContent.select(
                "[" + Constants.RDFa.ABOUT + "=" + Constants.BNODE_PREFIX + elementAbout + "]");
        if (elements.isEmpty()) {
            throw new SelectorGenerationException("No element with id " + elementAbout + " found in " + ft.getSource());
        }
        return selectorGenerators.generateSelectors(elements.toArray(new Element[]{}));
    }

    private Document loadTargetContent(FileOccurrenceTarget ft) {
        final Resource targetFile = resourceService.findRequired(ft.getSource());
        assert targetFile instanceof File;
        final String content = documentManager.loadFileContent((File) targetFile);
        return Jsoup.parse(content);
    }
}
