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
package cz.cvut.kbss.termit.service.document;

import cz.cvut.kbss.termit.exception.AnnotationGenerationException;
import cz.cvut.kbss.termit.model.AbstractTerm;
import cz.cvut.kbss.termit.model.assignment.TermOccurrence;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.util.debounce.Debounce;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;

/**
 * Creates annotations (term occurrences) for vocabulary terms.
 * <p>
 * The generated {@link TermOccurrence}s are assigned a special type so that it is clear they have been suggested by an
 * automated procedure and should be reviewed.
 */
@Service
public class AnnotationGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(AnnotationGenerator.class);

    private final DocumentManager documentManager;

    private final TermOccurrenceResolvers resolvers;

    private final TermOccurrenceSaver occurrenceSaver;

    @Autowired
    public AnnotationGenerator(DocumentManager documentManager,
                               TermOccurrenceResolvers resolvers,
                               TermOccurrenceSaver occurrenceSaver) {
        this.documentManager = documentManager;
        this.resolvers = resolvers;
        this.occurrenceSaver = occurrenceSaver;
    }

    /**
     * Generates annotations (term occurrences) for terms identified in the specified document.
     *
     * @param content Content of file with identified term occurrences
     * @param source  Source file of the annotated document
     */
    @Transactional
    public void generateAnnotations(InputStream content, File source) {
        final TermOccurrenceResolver occurrenceResolver = findResolverFor(source);
        LOG.debug("Resolving annotations of file {}.", source);
        occurrenceResolver.parseContent(content, source);
        occurrenceResolver.setExistingOccurrences(occurrenceSaver.getExistingOccurrences(source));
        final List<TermOccurrence> occurrences = occurrenceResolver.findTermOccurrences();
        saveAnnotatedContent(source, occurrenceResolver.getContent());
        occurrenceSaver.saveOccurrences(occurrences, source);
        LOG.trace("Finished generating annotations for file {}.", source);
    }

    private TermOccurrenceResolver findResolverFor(File file) {
        // This will allow us to potentially support different types of files
        final TermOccurrenceResolver htmlResolver = resolvers.htmlTermOccurrenceResolver();
        if (htmlResolver.supports(file)) {
            return htmlResolver;
        } else {
            throw new AnnotationGenerationException("Unsupported type of file " + file);
        }
    }

    private void saveAnnotatedContent(File file, InputStream input) {
        documentManager.saveFileContent(file, input);
    }

    /**
     * Generates annotations (term occurrences) for terms identified in the specified term's definition.
     *
     * @param content       Term definition with identified term occurrences
     * @param annotatedTerm Term whose definition was annotated
     */
    @Transactional
    @Debounce(value = "{annotatedTerm.getUri()}")
    public void generateAnnotations(InputStream content, AbstractTerm annotatedTerm) {
        // We assume the content (text analysis output) is HTML-compatible
        final TermOccurrenceResolver occurrenceResolver = resolvers.htmlTermOccurrenceResolver();
        LOG.debug("Resolving annotations of the definition of {}.", annotatedTerm);
        occurrenceResolver.parseContent(content, annotatedTerm);
        final List<TermOccurrence> occurrences = occurrenceResolver.findTermOccurrences();
        occurrenceSaver.saveOccurrences(occurrences, annotatedTerm);
        LOG.trace("Finished generating annotations for the definition of {}.", annotatedTerm);
    }
}
