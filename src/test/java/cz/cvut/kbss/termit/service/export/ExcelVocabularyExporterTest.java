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

import cz.cvut.kbss.termit.dto.export.TabularTermExportUtils;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.service.mapper.TermDtoMapper;
import cz.cvut.kbss.termit.service.mapper.TermDtoMapperImpl;
import cz.cvut.kbss.termit.service.repository.TermRepositoryService;
import cz.cvut.kbss.termit.util.Constants;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static cz.cvut.kbss.termit.service.export.ExcelVocabularyExporter.SHEET_NAME;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExcelVocabularyExporterTest {

    @Mock
    private TermRepositoryService termService;

    @Spy
    private TermDtoMapper dtoMapper = new TermDtoMapperImpl();

    @InjectMocks
    private ExcelVocabularyExporter sut;

    private final Vocabulary vocabulary = Generator.generateVocabularyWithId();

    @Test
    void exportVocabularyGlossaryOutputsExcelWorkbookWithSingleSheet() throws Exception {
        when(termService.findAllFull(vocabulary)).thenReturn(Collections.emptyList());
        final Resource result = sut.exportGlossary(vocabulary, exportConfig());
        assertNotNull(result);
        final XSSFWorkbook wb = new XSSFWorkbook(result.getInputStream());
        assertEquals(1, wb.getNumberOfSheets());
        assertEquals(0, wb.getSheetIndex(SHEET_NAME));
    }

    private static ExportConfig exportConfig() {
        return new ExportConfig(ExportType.SKOS, ExportFormat.EXCEL.getMediaType());
    }

    @Test
    void exportVocabularyGlossaryOutputsHeaderRowWithColumnNamesIntoSheet() throws Exception {
        final List<Term> terms = IntStream.range(0, 10).mapToObj(i -> Generator.generateTermWithId()).collect(
                Collectors.toList());
        when(termService.findAllFull(vocabulary)).thenReturn(terms);
        final Resource result = sut.exportGlossary(vocabulary, exportConfig());
        final XSSFWorkbook wb = new XSSFWorkbook(result.getInputStream());
        final XSSFSheet sheet = wb.getSheet(SHEET_NAME);
        assertNotNull(sheet);
        final XSSFRow row = sheet.getRow(0);
        assertNotNull(row);
        for (int i = 0; i < TabularTermExportUtils.EXPORT_COLUMNS.size(); i++) {
            assertEquals(TabularTermExportUtils.EXPORT_COLUMNS.get(i), row.getCell(i).getStringCellValue());
        }
    }

    @Test
    void exportVocabularyGlossaryOutputsGlossaryTermsIntoSheet() throws Exception {
        final List<Term> terms = IntStream.range(0, 10).mapToObj(i -> Generator.generateTermWithId()).collect(
                Collectors.toList());
        when(termService.findAllFull(vocabulary)).thenReturn(terms);
        final Resource result = sut.exportGlossary(vocabulary, exportConfig());
        final XSSFWorkbook wb = new XSSFWorkbook(result.getInputStream());
        final XSSFSheet sheet = wb.getSheet(SHEET_NAME);
        assertNotNull(sheet);
        // Plus header row
        assertEquals(terms.size(), sheet.getLastRowNum());
        for (int i = 1; i < sheet.getLastRowNum(); i++) {
            final XSSFRow row = sheet.getRow(i);
            final String id = row.getCell(0).getStringCellValue();
            assertTrue(terms.stream().anyMatch(t -> t.getUri().toString().equals(id)));
        }
    }

    @Test
    void supportsReturnsTrueForExcelMediaType() {
        assertTrue(sut.supports(Constants.MediaType.EXCEL));
    }

    @Test
    void supportsReturnsFalseForNonExcelMediaType() {
        assertFalse(sut.supports(MediaType.APPLICATION_JSON_VALUE));
    }
}
