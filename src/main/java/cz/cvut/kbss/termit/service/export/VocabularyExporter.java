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
package cz.cvut.kbss.termit.service.export;

import cz.cvut.kbss.termit.exception.UnsupportedOperationException;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.util.TypeAwareResource;

/**
 * Allows to export a vocabulary and assets related to it.
 */
public interface VocabularyExporter {

    /**
     * Exports the specified vocabulary in a form specified by the export configuration.
     *
     * @param vocabulary Vocabulary whose glossary should be exported
     * @param config     Export configuration
     * @return IO resource representing the exported glossary
     * @throws UnsupportedOperationException If the specified export type is not supported by the exporter
     */
    TypeAwareResource exportGlossary(Vocabulary vocabulary, ExportConfig config);

    /**
     * Checks whether this exporter supports the specified media type.
     *
     * @param mediaType Target media type for the export
     * @return Whether the media type is supported
     */
    boolean supports(String mediaType);
}
