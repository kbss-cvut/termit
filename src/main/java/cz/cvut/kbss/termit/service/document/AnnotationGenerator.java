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
import cz.cvut.kbss.termit.exception.TermItException;
import cz.cvut.kbss.termit.model.AbstractTerm;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.assignment.TermOccurrence;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.util.throttle.Throttle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Creates annotations (term occurrences) for vocabulary terms.
 * <p>
 * The generated {@link TermOccurrence}s are assigned a special type so that it is clear they have been suggested by an
 * automated procedure and should be reviewed.
 */
@Service
public class AnnotationGenerator {

    private static final long THREAD_JOIN_TIMEOUT = 1000L * 60; // 1 minute

    private static final Logger LOG = LoggerFactory.getLogger(AnnotationGenerator.class);

    private final DocumentManager documentManager;

    private final TermOccurrenceResolvers resolvers;

    private final TermOccurrenceSaver occurrenceSaver;

    @Autowired
    public AnnotationGenerator(DocumentManager documentManager, TermOccurrenceResolvers resolvers,
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
    @Throttle("{source.getUri()}")
    public void generateAnnotations(InputStream content, File source) {
        final TermOccurrenceResolver occurrenceResolver = findResolverFor(source);
        LOG.debug("Resolving annotations of file {}.", source);
        occurrenceResolver.parseContent(content, source);
        occurrenceResolver.setExistingOccurrences(occurrenceSaver.getExistingOccurrences(source));
        findAndSaveTermOccurrences(source, occurrenceResolver);
        LOG.trace("Finished generating annotations for file {}.", source);
    }

    /**
     * Calls {@link TermOccurrenceResolver#findTermOccurrences(TermOccurrenceResolver.OccurrenceConsumer)} on {@code #occurrenceResolver}
     * creating new thread that will save any found occurrence in parallel.
     * Saves annotated content ({@link #saveAnnotatedContent(File, InputStream)} when the source is a {@link File}.
     */
    private void findAndSaveTermOccurrences(Asset<?> source, TermOccurrenceResolver occurrenceResolver) {
        AtomicBoolean finished = new AtomicBoolean(false);
        // alternatively, SynchronousQueue could be used, but this allows to have some space as buffer
        final ArrayBlockingQueue<TermOccurrence> toSave = new ArrayBlockingQueue<>(10);
        // not limiting the queue size would result in OutOfMemoryError

        FutureTask<Void> findTask = new FutureTask<>(() -> {
            try {
                LOG.trace("Resolving term occurrences for {}.", source);
                occurrenceResolver.findTermOccurrences(toSave::put);
                LOG.trace("Finished resolving term occurrences for {}.", source);
                LOG.trace("Saving term occurrences for {}.", source);
                if (source instanceof File sourceFile) {
                    saveAnnotatedContent(sourceFile, occurrenceResolver.getContent());
                }
                LOG.trace("Term occurrences saved for {}.", source);
            } finally {
                finished.set(true);
            }
            return null;
        });
        Thread finder = new Thread(findTask);
        finder.setName("AnnotationGenerator-TermOccurrenceResolver");
        finder.start();

        occurrenceSaver.saveFromQueue(source, finished, toSave);

        try {
            findTask.get(); // propagates exceptions
            finder.join(THREAD_JOIN_TIMEOUT);
        } catch (InterruptedException e) {
            LOG.error("Thread interrupted while saving annotations of file {}.", source);
            Thread.currentThread().interrupt();
            throw new TermItException(e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw new TermItException(e);
        }
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
    @Throttle(value = "{#annotatedTerm.getUri()}")
    public void generateAnnotations(InputStream content, AbstractTerm annotatedTerm) {
        // We assume the content (text analysis output) is HTML-compatible
        final TermOccurrenceResolver occurrenceResolver = resolvers.htmlTermOccurrenceResolver();
        LOG.debug("Resolving annotations of the definition of {}.", annotatedTerm);
        occurrenceResolver.parseContent(content, annotatedTerm);
        occurrenceResolver.findTermOccurrences(o -> occurrenceSaver.saveOccurrence(o, annotatedTerm));
        LOG.trace("Finished generating annotations for the definition of {}.", annotatedTerm);
    }
}
