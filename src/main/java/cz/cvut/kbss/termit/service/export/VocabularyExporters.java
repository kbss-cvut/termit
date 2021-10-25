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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class VocabularyExporters {

    private final List<VocabularyExporter> exporters;

    @Autowired
    public VocabularyExporters(List<VocabularyExporter> exporters) {
        this.exporters = exporters;
    }

    /**
     * Exports glossary of the specified vocabulary as the specified media type (if supported).
     * <p>
     * If the media type is not supported, an empty {@link Optional} is returned.
     *
     * @param vocabulary Vocabulary to export
     * @param mediaType  Target media type
     * @return Exported data wrapped in an {@code Optional}
     */
    public Optional<TypeAwareResource> exportGlossary(Vocabulary vocabulary, String mediaType) {
        return resolveExporter(mediaType).map(e -> e.exportGlossary(vocabulary));
    }

    private Optional<VocabularyExporter> resolveExporter(String mediaType) {
        Objects.requireNonNull(mediaType);
        return exporters.stream().filter(e -> e.supports(mediaType)).findFirst();
    }

    /**
     * Exports glossary of the specified vocabulary with relevant references to terms in other vocabularies as the specified media type (if supported).
     * <p>
     * If the media type is not supported, an empty {@link Optional} is returned.
     *
     * @param vocabulary Vocabulary to export
     * @param properties Collection of properties representing references to terms in other vocabularies to include in the export
     * @param mediaType  Target media type
     * @return Exported data wrapped in an {@code Optional}
     */
    public Optional<TypeAwareResource> exportGlossaryWithReferences(Vocabulary vocabulary,
                                                                    Collection<String> properties, String mediaType) {
        return resolveExporter(mediaType).map(e -> e.exportGlossaryWithReferences(vocabulary, properties));
    }
}
