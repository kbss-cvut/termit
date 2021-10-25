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
import cz.cvut.kbss.termit.util.Constants;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

import java.util.Comparator;
import java.util.List;

import static cz.cvut.kbss.termit.service.export.ExcelVocabularyExporter.SHEET_NAME;
import static org.junit.jupiter.api.Assertions.*;

class ExcelVocabularyExporterTest extends VocabularyExporterTestBase {

    @Autowired
    private ExcelVocabularyExporter sut;

    @BeforeEach
    void setUp() {
        super.setUp();
    }

    @Test
    void exportVocabularyGlossaryOutputsExcelWorkbookWithSingleSheet() throws Exception {
        final Resource result = sut.exportGlossary(vocabulary);
        assertNotNull(result);
        final XSSFWorkbook wb = new XSSFWorkbook(result.getInputStream());
        assertEquals(1, wb.getNumberOfSheets());
        assertEquals(0, wb.getSheetIndex(SHEET_NAME));
    }

    @Test
    void exportVocabularyGlossaryOutputsHeaderRowWithColumnNamesIntoSheet() throws Exception {
        final Resource result = sut.exportGlossary(vocabulary);
        final XSSFWorkbook wb = new XSSFWorkbook(result.getInputStream());
        final XSSFSheet sheet = wb.getSheet(SHEET_NAME);
        assertNotNull(sheet);
        final XSSFRow row = sheet.getRow(0);
        assertNotNull(row);
        for (int i = 0; i < Term.EXPORT_COLUMNS.size(); i++) {
            assertEquals(Term.EXPORT_COLUMNS.get(i), row.getCell(i).getStringCellValue());
        }
    }

    @Test
    void exportVocabularyGlossaryOutputsGlossaryTermsIntoSheet() throws Exception {
        final List<Term> terms = generateTerms();
        final Resource result = sut.exportGlossary(vocabulary);
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
    void exportVocabularyGlossaryOutputsGlossaryTermsOrderedByLabel() throws Exception {
        final List<Term> terms = generateTerms();
        terms.sort(Comparator.comparing((Term t) -> t.getLabel().get(Environment.LANGUAGE)));
        final Resource result = sut.exportGlossary(vocabulary);
        final XSSFWorkbook wb = new XSSFWorkbook(result.getInputStream());
        final XSSFSheet sheet = wb.getSheet(SHEET_NAME);
        assertNotNull(sheet);
        // Plus header row
        assertEquals(terms.size(), sheet.getLastRowNum());
        for (int i = 1; i < sheet.getLastRowNum(); i++) {
            final XSSFRow row = sheet.getRow(i);
            final String id = row.getCell(0).getStringCellValue();
            assertEquals(terms.get(i - 1).getUri().toString(), id);
        }
    }

    @Test
    void supportsReturnsTrueForExcelMediaType() {
        assertTrue(sut.supports(Constants.Excel.MEDIA_TYPE));
    }

    @Test
    void supportsReturnsFalseForNonExcelMediaType() {
        assertFalse(sut.supports(MediaType.APPLICATION_JSON_VALUE));
    }
}
