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
package cz.cvut.kbss.termit.service.export;

import cz.cvut.kbss.termit.exception.ResourceNotFoundException;
import cz.cvut.kbss.termit.exception.UnsupportedOperationException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.service.export.util.TypeAwareByteArrayResource;
import cz.cvut.kbss.termit.service.repository.TermRepositoryService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.TypeAwareResource;
import cz.cvut.kbss.termit.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;
import java.util.Objects;

/**
 * Implements vocabulary export to CSV.
 */
@Service("csv")
public class CsvVocabularyExporter implements VocabularyExporter {

    private static final Logger LOG = LoggerFactory.getLogger(CsvVocabularyExporter.class);

    private static final String DIRECTORY = "template";
    private static final String TEMPLATE_FILE = "export.csv";

    private final TermRepositoryService termService;

    private final Configuration config;

    @Autowired
    public CsvVocabularyExporter(TermRepositoryService termService, Configuration config) {
        this.termService = termService;
        this.config = config;
    }

    @Override
    public TypeAwareResource exportGlossary(Vocabulary vocabulary, ExportConfig config) {
        Objects.requireNonNull(vocabulary);
        Objects.requireNonNull(config);
        if (ExportType.SKOS == config.getType()) {
            return exportGlossary(vocabulary);
        }
        throw new UnsupportedOperationException("Unsupported export type " + config.getType());
    }

    private TypeAwareByteArrayResource exportGlossary(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        final StringBuilder export = new StringBuilder(loadHeader().trim());
        final List<Term> terms = termService.findAllFull(vocabulary);
        final CsvTermExporter termExporter = new CsvTermExporter();
        terms.forEach(t -> export.append('\n').append(termExporter.export(t)));
        return new TypeAwareByteArrayResource(export.toString().getBytes(), ExportFormat.CSV.getMediaType(),
                                              ExportFormat.CSV.getFileExtension());
    }

    private String loadHeader() {
        try {
            // First try loading a localized header
            return Utils.loadClasspathResource(DIRECTORY + File.separator + config.getPersistence()
                                                                                  .getLanguage() + File.separator + TEMPLATE_FILE);
        } catch (ResourceNotFoundException e) {
            // If we do not find it, fall back to the default one
            LOG.warn("Unable to find localized CSV export header. Falling back to the default one.", e);
            return Utils.loadClasspathResource(
                    DIRECTORY + File.separator + Constants.DEFAULT_LANGUAGE + File.separator + TEMPLATE_FILE);
        }
    }

    @Override
    public boolean supports(String mediaType) {
        return Objects.equals(ExportFormat.CSV.getMediaType(), mediaType);
    }
}
