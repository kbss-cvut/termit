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
     * @param target    Asset in which the occurrence is to be found
     * @param elementId Element representing the occurrence
     * @return Set of generated selectors for the occurrence element
     * @throws UnsupportedOperationException If the specified target is not supported
     * @throws SelectorGenerationException   If unable to generate selectors
     */
    public Set<Selector> createSelectors(@Nonnull OccurrenceTarget target, @Nonnull String elementId) {
        Objects.requireNonNull(target);
        Objects.requireNonNull(elementId);
        if (target instanceof DefinitionalOccurrenceTarget) {
            throw new UnsupportedOperationException("Cannot create selectors for a term definitional occurrence.");
        }
        final FileOccurrenceTarget ft = (FileOccurrenceTarget) target;
        final Document targetContent = loadTargetContent(ft);
        final Elements elements = targetContent.select("#" + elementId);
        if (elements.isEmpty()) {
            throw new SelectorGenerationException("No element with id " + elementId + " found in " + ft.getSource());
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
