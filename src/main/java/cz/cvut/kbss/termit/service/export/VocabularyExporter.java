/**
 * TermIt
 * Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.service.export;

import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.util.TypeAwareResource;

import java.util.Collection;

/**
 * Allows to export a vocabulary and assets related to it.
 */
public interface VocabularyExporter {

    /**
     * Gets a resource representation of the specified vocabulary's glossary.
     * <p>
     * The resource can be, for example, a CSV file.
     *
     * @param vocabulary Vocabulary whose glossary should be exported
     * @return IO resource representing the exported glossary
     * @see #exportGlossaryWithReferences(Vocabulary, Collection)
     */
    TypeAwareResource exportGlossary(Vocabulary vocabulary);

    /**
     * Gets a resource representation of the specified vocabulary's glossary including external terms referenced by the vocabulary's terms.
     * <p>
     * That is, besides the exported glossary specified by the argument, terms from other vocabularies referenced by terms from the exported
     * glossary via one of the specified properties are included in the result as well. If {@code properties} are empty, this method behaves exactly
     * as {@link #exportGlossary(Vocabulary)}. Only SKOS-based properties (e.g, skos:exactMatch, skos:relatedMatch) are supported.
     *
     * @param vocabulary Vocabulary whose glossary should be exported
     * @param properties Properties used to identify references to terms from other glossaries (e.g., skos:exactMatch)
     * @return IO resource representing the exported glossary
     * @see #exportGlossary(Vocabulary)
     */
    TypeAwareResource exportGlossaryWithReferences(Vocabulary vocabulary, Collection<String> properties);

    /**
     * Checks whether this exporter supports the specified media type.
     *
     * @param mediaType Target media type for the export
     * @return Whether the media type is supported
     */
    boolean supports(String mediaType);
}
