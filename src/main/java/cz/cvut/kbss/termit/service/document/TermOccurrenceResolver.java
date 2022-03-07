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
package cz.cvut.kbss.termit.service.document;

import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.assignment.*;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.service.repository.TermRepositoryService;

import java.io.InputStream;
import java.net.URI;
import java.util.List;

/**
 * Base class for resolving term occurrences in an annotated document.
 */
public abstract class TermOccurrenceResolver {

    protected final TermRepositoryService termService;

    protected TermOccurrenceResolver(TermRepositoryService termService) {
        this.termService = termService;
    }

    /**
     * Parses the specified input into some abstract representation from which new terms and term occurrences can be
     * extracted.
     * <p>
     * Note that this method has to be called before calling {@link #findTermOccurrences()}.
     *
     * @param input  The input to parse
     * @param source Original source of the input. Used for term occurrence generation
     */
    public abstract void parseContent(InputStream input, Asset<?> source);

    /**
     * Gets the content which was previously parsed and processed by this instance.
     * <p>
     * This may return a different data that what was originally passed in {@link #parseContent(InputStream, Asset)}, as
     * the processing might have augmented the content.
     *
     * @return {@code InputStream} with processed content
     */
    public abstract InputStream getContent();

    /**
     * Finds term occurrences in the input stream.
     * <p>
     * {@link #parseContent(InputStream, Asset)} has to be called prior to this method.
     *
     * @return List of term occurrences identified in the input
     * @see #parseContent(InputStream, Asset)
     */
    public abstract List<TermOccurrence> findTermOccurrences();

    /**
     * Checks whether this resolver supports the specified source file type.
     *
     * @param source File to check
     * @return Support status
     */
    public abstract boolean supports(Asset<?> source);

    /**
     * Creates occurrence of a term with the specified URI with target source in the specified asset.
     *
     * @param termUri URI of the occurring term
     * @param source  Source in which the term occurred
     * @return New {@code TermOccurrence} instance
     */
    protected TermOccurrence createOccurrence(URI termUri, Asset<?> source) {
        final TermOccurrence occurrence;
        if (source instanceof File) {
            final FileOccurrenceTarget target = new FileOccurrenceTarget((File) source);
            occurrence = new TermFileOccurrence(termUri, target);
        } else if (source instanceof Term) {
            final DefinitionalOccurrenceTarget target = new DefinitionalOccurrenceTarget((Term) source);
            occurrence = new TermDefinitionalOccurrence(termUri, target);
        } else {
            throw new IllegalArgumentException("Unsupported term occurrence source " + source);
        }
        return occurrence;
    }
}
