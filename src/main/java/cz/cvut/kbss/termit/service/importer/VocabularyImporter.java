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

import cz.cvut.kbss.termit.exception.importing.VocabularyExistsException;
import cz.cvut.kbss.termit.model.Vocabulary;
import jakarta.annotation.Nonnull;

import java.io.InputStream;
import java.net.URI;
import java.util.function.Consumer;

/**
 * Supports importing vocabularies.
 */
public interface VocabularyImporter {

    /**
     * Imports vocabulary from the specified data.
     * <p>
     * Import configuration allows to specify handling of existing vocabulary data or target vocabulary identifier.
     *
     * @param config Import configuration
     * @param data   Data to import
     * @return Imported vocabulary
     * @throws VocabularyExistsException If a vocabulary/glossary with the same identifier already exists and
     *                                   {@code config} does not allow renaming or overwriting
     * @throws IllegalArgumentException  Indicates invalid input data, e.g., no input streams, missing language tags
     *                                   etc.
     */
    Vocabulary importVocabulary(@Nonnull ImportConfiguration config, @Nonnull ImportInput data);

    /**
     * Imports term translations from the specified data into the specified vocabulary.
     * <p>
     * Only translations of existing terms are imported, no new terms are created. Only translations of multilingual
     * attributes are imported. If a value in the specified language exists in the repository, it is preserved.
     *
     * @param vocabularyIri Vocabulary identifier
     * @param data          Data to import
     * @return Vocabulary whose content was changed
     * @throws IllegalArgumentException Indicates invalid input data, e.g., no input streams, missing language tags
     *                                  etc.
     */
    Vocabulary importTermTranslations(@Nonnull URI vocabularyIri, @Nonnull ImportInput data);

    /**
     * Vocabulary import configuration.
     *
     * @param allowReIdentify Whether to allow modifying identifiers when repository already contains data with matching
     *                        identifiers
     * @param vocabularyIri   Identifier of the target vocabulary, optional. If specified, any pre-existing data are
     *                        overwritten
     * @param prePersist      Procedure to call before persisting the resulting vocabulary
     */
    record ImportConfiguration(boolean allowReIdentify, URI vocabularyIri,
                               @Nonnull Consumer<Vocabulary> prePersist) {
    }

    /**
     * Data to import.
     *
     * @param mediaType Media type of the imported data
     * @param data      Streams containing the data
     */
    record ImportInput(@Nonnull String mediaType, InputStream... data) {
    }
}
