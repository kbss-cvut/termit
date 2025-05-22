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
package cz.cvut.kbss.termit.service.importer;

import cz.cvut.kbss.termit.exception.importing.UnsupportedImportMediaTypeException;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.dao.skos.SKOSImporter;
import cz.cvut.kbss.termit.service.importer.excel.ExcelImporter;
import jakarta.annotation.Nonnull;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.net.URI;

/**
 * Ensures correct importer is invoked for provided media types.
 */
@Component
public class VocabularyImporters implements VocabularyImporter {

    private final ApplicationContext appContext;

    public VocabularyImporters(ApplicationContext appContext) {
        this.appContext = appContext;
    }

    @Override
    public Vocabulary importVocabulary(@Nonnull ImportConfiguration config, @Nonnull ImportInput data) {
        return resolveImporter(data.mediaType()).importVocabulary(config, data);
    }

    private VocabularyImporter resolveImporter(String mediaType) {
        if (SKOSImporter.supportsMediaType(mediaType)) {
            return getSkosImporter();
        } else if (ExcelImporter.supportsMediaType(mediaType)) {
            return getExcelImporter();
        }
        throw new UnsupportedImportMediaTypeException(
                "Unsupported media type '" + mediaType + "' for vocabulary import.");
    }

    @Override
    public Vocabulary importTermTranslations(@Nonnull URI vocabularyIri, @Nonnull ImportInput data) {
        return resolveImporter(data.mediaType()).importTermTranslations(vocabularyIri, data);
    }

    private VocabularyImporter getSkosImporter() {
        return appContext.getBean(SKOSImporter.class);
    }

    private VocabularyImporter getExcelImporter() {
        return appContext.getBean(ExcelImporter.class);
    }
}
