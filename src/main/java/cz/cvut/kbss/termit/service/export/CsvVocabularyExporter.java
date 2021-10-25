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

import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.service.export.util.TypeAwareByteArrayResource;
import cz.cvut.kbss.termit.service.repository.TermRepositoryService;
import cz.cvut.kbss.termit.util.TypeAwareResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static cz.cvut.kbss.termit.util.CsvUtils.FILE_EXTENSION;
import static cz.cvut.kbss.termit.util.CsvUtils.MEDIA_TYPE;

@Service("csv")
public class CsvVocabularyExporter implements VocabularyExporter {

    private final TermRepositoryService termService;

    @Autowired
    public CsvVocabularyExporter(TermRepositoryService termService) {
        this.termService = termService;
    }

    @Override
    public TypeAwareResource exportGlossary(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        final StringBuilder export = new StringBuilder(String.join(",", Term.EXPORT_COLUMNS));
        final List<Term> terms = termService.findAll(vocabulary);
        terms.forEach(t -> export.append('\n').append(t.toCsv()));
        return new TypeAwareByteArrayResource(export.toString().getBytes(), MEDIA_TYPE, FILE_EXTENSION);
    }

    @Override
    public TypeAwareResource exportGlossaryWithReferences(Vocabulary vocabulary,
                                                          Collection<String> properties) {
        throw new UnsupportedOperationException("Exporting glossary with references to CSV is not supported.");
    }

    @Override
    public boolean supports(String mediaType) {
        return Objects.equals(MEDIA_TYPE, mediaType);
    }
}
