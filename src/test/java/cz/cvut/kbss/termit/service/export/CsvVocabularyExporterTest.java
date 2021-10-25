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

import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.util.CsvUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class CsvVocabularyExporterTest extends VocabularyExporterTestBase {

    @Autowired
    private CsvVocabularyExporter sut;

    @BeforeEach
    void setUp() {
        super.setUp();
    }

    @Test
    void exportVocabularyGlossaryOutputsHeaderContainingColumnNamesIntoResult() throws Exception {
        final Resource result = sut.exportGlossary(vocabulary);
        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(result.getInputStream()))) {
            final String header = reader.readLine();
            assertEquals(String.join(",", Term.EXPORT_COLUMNS), header);
        }
    }

    @Test
    void exportVocabularyGlossaryOutputsTermsContainedInVocabularyAsCsv() throws Exception {
        final List<Term> terms = generateTerms();
        final Resource result = sut.exportGlossary(vocabulary);
        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(result.getInputStream()))) {
            final List<String> lines = reader.lines().collect(Collectors.toList());
            // terms + header
            assertEquals(terms.size() + 1, lines.size());
            for (int i = 1; i < lines.size(); i++) {
                final String line = lines.get(i);
                final URI id = URI.create(line.substring(0, line.indexOf(',')));
                assertTrue(terms.stream().anyMatch(t -> t.getUri().equals(id)));
            }
        }
    }

    @Test
    void exportVocabularyGlossaryExportsTermsOrderedByLabel() throws Exception {
        final List<Term> terms = generateTerms();
        terms.sort(Comparator.comparing((Term t) -> t.getLabel().get(Environment.LANGUAGE)));
        final Resource result = sut.exportGlossary(vocabulary);
        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(result.getInputStream()))) {
            final List<String> lines = reader.lines().collect(Collectors.toList());
            for (int i = 0; i < terms.size(); i++) {
                final String line = lines.get(i + 1);
                final URI id = URI.create(line.substring(0, line.indexOf(',')));
                assertEquals(terms.get(i).getUri(), id);
            }
        }
    }

    @Test
    void supportsReturnsTrueForCsvMediaType() {
        assertTrue(sut.supports(CsvUtils.MEDIA_TYPE));
    }

    @Test
    void supportsReturnsFalseNonCsvMediaType() {
        assertFalse(sut.supports(MediaType.APPLICATION_JSON_VALUE));
    }
}
