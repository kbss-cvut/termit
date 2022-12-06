/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If not, see
 * <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.service.export;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.service.export.util.TabularTermExportUtils;
import cz.cvut.kbss.termit.service.repository.TermRepositoryService;
import cz.cvut.kbss.termit.util.Constants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CsvVocabularyExporterTest {

    @Mock
    private TermRepositoryService termService;

    @InjectMocks
    private CsvVocabularyExporter sut;

    private final Vocabulary vocabulary = Generator.generateVocabularyWithId();

    @Test
    void exportVocabularyGlossaryOutputsHeaderContainingColumnNamesIntoResult() throws Exception {
        when(termService.findAllFull(vocabulary)).thenReturn(Collections.emptyList());
        final Resource result = sut.exportGlossary(vocabulary, exportConfig());
        try (final BufferedReader reader = new BufferedReader(new InputStreamReader(result.getInputStream()))) {
            final String header = reader.readLine();
            assertEquals(String.join(",", TabularTermExportUtils.EXPORT_COLUMNS), header);
        }
    }

    private static ExportConfig exportConfig() {
        return new ExportConfig(ExportType.SKOS, ExportFormat.CSV.getMediaType());
    }

    @Test
    void exportVocabularyGlossaryOutputsTermsContainedInVocabularyAsCsv() throws Exception {
        final List<Term> terms = IntStream.range(0, 10).mapToObj(i -> Generator.generateTermWithId()).collect(
                Collectors.toList());
        when(termService.findAllFull(vocabulary)).thenReturn(terms);
        final Resource result = sut.exportGlossary(vocabulary, exportConfig());
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
    void supportsReturnsTrueForCsvMediaType() {
        assertTrue(sut.supports(Constants.MediaType.CSV));
    }

    @Test
    void supportsReturnsFalseNonCsvMediaType() {
        assertFalse(sut.supports(MediaType.APPLICATION_JSON_VALUE));
    }
}
