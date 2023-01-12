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

import com.neovisionaries.i18n.LanguageCode;
import cz.cvut.kbss.termit.dto.PrefixDeclaration;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.VocabularyService;
import cz.cvut.kbss.termit.service.repository.TermRepositoryService;
import cz.cvut.kbss.termit.util.Constants;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.oneOf;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExcelVocabularyExporterTest {

    @Mock
    private TermRepositoryService termService;

    @Mock
    private VocabularyService vocabularyService;

    @InjectMocks
    private ExcelVocabularyExporter sut;

    private final Vocabulary vocabulary = Generator.generateVocabularyWithId();

    private static ExportConfig exportConfig() {
        return new ExportConfig(ExportType.SKOS, ExportFormat.EXCEL.getMediaType());
    }

    @Test
    void exportGlossaryOutputsGlossaryTermsIntoSheet() throws Exception {
        when(vocabularyService.resolvePrefix(any())).thenReturn(PrefixDeclaration.EMPTY_PREFIX);
        final List<Term> terms = IntStream.range(0, 5).mapToObj(i -> Generator.generateTermWithId()).collect(
                Collectors.toList());
        when(termService.findAllFull(vocabulary)).thenReturn(terms);
        final Resource result = sut.exportGlossary(vocabulary, exportConfig());
        final XSSFWorkbook wb = new XSSFWorkbook(result.getInputStream());
        final XSSFSheet sheet = wb.getSheet(LanguageCode.getByCodeIgnoreCase(Constants.DEFAULT_LANGUAGE).getName());
        assertNotNull(sheet);
        int i;
        for (i = 1; i < terms.size(); i++) {
            final XSSFRow row = sheet.getRow(i);
            final String id = row.getCell(0).getStringCellValue();
            assertTrue(terms.stream().anyMatch(t -> t.getUri().toString().equals(id)));
        }
        assertNull(sheet.getRow(i + 1));
    }

    @Test
    void supportsReturnsTrueForExcelMediaType() {
        assertTrue(sut.supports(Constants.MediaType.EXCEL));
    }

    @Test
    void supportsReturnsFalseForNonExcelMediaType() {
        assertFalse(sut.supports(MediaType.APPLICATION_JSON_VALUE));
    }

    @Test
    void exportGlossaryUsesVocabularyServiceToRetrievePrefixes() {
        final URI vocabularyUri = Generator.generateUri();
        final URI exactMatchVocabularyUri = Generator.generateUri();
        final List<Term> terms = IntStream.range(0, 5).mapToObj(i -> Generator.generateTermWithId(vocabularyUri))
                                          .collect(Collectors.toList());
        terms.get(0).setExactMatchTerms(
                Collections.singleton(new TermInfo(Generator.generateTermWithId(exactMatchVocabularyUri))));
        when(vocabularyService.resolvePrefix(any())).thenReturn(PrefixDeclaration.EMPTY_PREFIX);
        when(termService.findAllFull(vocabulary)).thenReturn(terms);

        sut.exportGlossary(vocabulary, exportConfig());
        verify(vocabularyService).resolvePrefix(vocabularyUri);
        verify(vocabularyService).resolvePrefix(exactMatchVocabularyUri);
    }

    @Test
    void exportGlossaryGeneratesExtraSheetWithPrefixMapping() throws Exception {
        final URI vocabularyUri = vocabulary.getUri();
        final String vocabularyPrefix = "pOne";
        when(vocabularyService.resolvePrefix(vocabularyUri)).thenReturn(new PrefixDeclaration(vocabularyPrefix,
                                                                                              IdentifierResolver.extractIdentifierNamespace(
                                                                                                      vocabularyUri)));
        final URI exactMatchVocabularyUri = Generator.generateUri();
        final String exactMatchVocabularyPrefix = "pTwo";
        when(vocabularyService.resolvePrefix(exactMatchVocabularyUri)).thenReturn(
                new PrefixDeclaration(exactMatchVocabularyPrefix,
                                      IdentifierResolver.extractIdentifierNamespace(exactMatchVocabularyUri)));
        final List<Term> terms = IntStream.range(0, 5).mapToObj(i -> Generator.generateTermWithId(vocabularyUri))
                                          .collect(Collectors.toList());
        terms.get(0).setExactMatchTerms(
                Collections.singleton(new TermInfo(Generator.generateTermWithId(exactMatchVocabularyUri))));
        when(termService.findAllFull(vocabulary)).thenReturn(terms);
        final Resource result = sut.exportGlossary(vocabulary, exportConfig());
        final XSSFWorkbook wb = new XSSFWorkbook(result.getInputStream());
        final XSSFSheet sheet = wb.getSheetAt(wb.getNumberOfSheets() - 1);
        assertNotNull(sheet);
        assertEquals(ExcelVocabularyExporter.PREFIX_COLUMN, sheet.getSheetName());
        // First row is the header
        final Row header = sheet.getRow(0);
        assertEquals(ExcelVocabularyExporter.PREFIX_COLUMN, header.getCell(0).getStringCellValue());
        assertEquals(ExcelVocabularyExporter.NAMESPACE_COLUMN, header.getCell(1).getStringCellValue());
        for (int i = 1; i < 3; i++) {
            final Row r = sheet.getRow(i);
            assertNotNull(r);
            assertThat(r.getCell(0).getStringCellValue(), oneOf(vocabularyPrefix, exactMatchVocabularyPrefix));
            assertThat(r.getCell(1).getStringCellValue(),
                       oneOf(IdentifierResolver.extractIdentifierNamespace(vocabularyUri),
                             IdentifierResolver.extractIdentifierNamespace(exactMatchVocabularyUri)));
        }
    }

    @Test
    void exportGlossaryOutputsLocalizedVersionsOfTermsIntoSeparateSheets() throws Exception {
        when(vocabularyService.resolvePrefix(any())).thenReturn(PrefixDeclaration.EMPTY_PREFIX);
        final String[] languages = {"en", "cs"};
        final List<Term> terms = List.of(Generator.generateMultiLingualTerm(languages),
                                         Generator.generateMultiLingualTerm(languages));
        when(termService.findAllFull(vocabulary)).thenReturn(terms);
        final Resource result = sut.exportGlossary(vocabulary, exportConfig());
        final XSSFWorkbook wb = new XSSFWorkbook(result.getInputStream());
        for (String langCode : languages) {
            final XSSFSheet sheet = wb.getSheet(LanguageCode.getByCodeIgnoreCase(langCode).getName());
            assertNotNull(sheet);
            int i;
            for (i = 1; i < terms.size(); i++) {
                final XSSFRow row = sheet.getRow(i);
                final String id = row.getCell(0).getStringCellValue();
                assertTrue(terms.stream().anyMatch(t -> t.getUri().toString().equals(id)));
            }
            assertNull(sheet.getRow(i + 1));
        }
    }

    @Test
    void exportGlossarySkipsNullLanguages() throws Exception {
        when(vocabularyService.resolvePrefix(any())).thenReturn(PrefixDeclaration.EMPTY_PREFIX);
        final String[] languages = {"en", "cs"};
        final List<Term> terms = List.of(Generator.generateMultiLingualTerm(languages),
                                         Generator.generateMultiLingualTerm(languages));
        terms.get(0).getLabel().set("Language-less");
        when(termService.findAllFull(vocabulary)).thenReturn(terms);
        final Resource result = sut.exportGlossary(vocabulary, exportConfig());
        final XSSFWorkbook wb = new XSSFWorkbook(result.getInputStream());
        // Glossary sheets + prefix sheet
        assertEquals(languages.length + 1, wb.getNumberOfSheets());
    }
}
