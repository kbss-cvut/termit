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
import cz.cvut.kbss.termit.persistence.dao.skos.SKOSExporter;
import cz.cvut.kbss.termit.util.TypeAwareByteArrayResource;
import cz.cvut.kbss.termit.util.TypeAwareResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Exports vocabulary glossary in a SKOS-compatible format.
 */
public abstract class SKOSVocabularyExporter implements VocabularyExporter {

    private static final Logger LOG = LoggerFactory.getLogger(SKOSVocabularyExporter.class);

    private final ApplicationContext context;

    protected SKOSVocabularyExporter(ApplicationContext context) {
        this.context = context;
    }

    /**
     * This method ensures new instances of the prototype-scoped bean are returned on every call.
     */
    private SKOSExporter getSKOSExporter() {
        return context.getBean(SKOSExporter.class);
    }

    @Override
    @Transactional(readOnly = true)
    public TypeAwareResource exportGlossary(Vocabulary vocabulary, ExportConfig config) {
        Objects.requireNonNull(vocabulary);
        Objects.requireNonNull(config);
        LOG.debug("Exporting glossary of vocabulary {} as {}.", vocabulary, config.getType());
        final SKOSExporter skosExporter = getSKOSExporter();
        switch (config.getType()) {
            case SKOS:
                skosExporter.exportGlossary(vocabulary);
                break;
            case SKOS_FULL:
                skosExporter.exportFullGlossary(vocabulary);
                break;
            case SKOS_WITH_REFERENCES:
                skosExporter.exportGlossaryWithReferences(vocabulary, config.getReferenceProperties());
                break;
            case SKOS_FULL_WITH_REFERENCES:
                skosExporter.exportFullGlossaryWithReferences(vocabulary, config.getReferenceProperties());
                break;
            default:
                throw new UnsupportedOperationException("Unsupported export type " + config.getType());
        }
        final TypeAwareResource res = new TypeAwareByteArrayResource(skosExporter.exportAs(exportFormat()),
                                                                     exportFormat().getMediaType(),
                                                                     exportFormat().getFileExtension());
        LOG.trace("Export finished successfully.");
        return res;
    }

    protected abstract ExportFormat exportFormat();

    @Override
    public boolean supports(String mediaType) {
        return Objects.equals(exportFormat().getMediaType(), mediaType);
    }
}
