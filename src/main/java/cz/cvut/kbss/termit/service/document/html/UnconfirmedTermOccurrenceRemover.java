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

import cz.cvut.kbss.termit.exception.FileContentProcessingException;
import cz.cvut.kbss.termit.util.TypeAwareByteArrayResource;
import cz.cvut.kbss.termit.util.TypeAwareResource;
import jakarta.annotation.Nonnull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * Removes unconfirmed term occurrences from content.
 */
public class UnconfirmedTermOccurrenceRemover {

    private static final Logger LOG = LoggerFactory.getLogger(UnconfirmedTermOccurrenceRemover.class);

    /**
     * Removes unconfirmed term occurrences from the specified input.
     * <p>
     * Removing such occurrences means the corresponding elements are replaced with their text content.
     * <p>
     * An occurrence is considered unconfirmed when it has a confidence score, confirmed occurrences do not have
     * scores.
     *
     * @param input Input to process
     * @return Processed content
     */
    public TypeAwareResource removeUnconfirmedOccurrences(@Nonnull TypeAwareResource input) {
        LOG.trace("Removing unconfirmed occurrences from file {}.", input.getFilename());
        try {
            final Document doc = Jsoup.parse(input.getInputStream(), StandardCharsets.UTF_8.name(), "");
            doc.outputSettings().prettyPrint(false);
            final Elements spanElements = doc.select("span[score]");
            spanElements.forEach(Node::unwrap);

            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out));
            writer.write(doc.toString()); // Write modified HTML to output stream
            writer.close();
            return new TypeAwareByteArrayResource(out.toByteArray(), input.getMediaType().orElse(null),
                                                  input.getFileExtension().orElse(null));
        } catch (IOException e) {
            throw new FileContentProcessingException("Unable to read resource for unconfirmed occurrence removal.", e);
        }
    }
}
