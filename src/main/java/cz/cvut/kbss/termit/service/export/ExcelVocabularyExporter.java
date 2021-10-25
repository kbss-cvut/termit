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

import cz.cvut.kbss.termit.exception.TermItException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.service.export.util.TypeAwareByteArrayResource;
import cz.cvut.kbss.termit.service.repository.TermRepositoryService;
import cz.cvut.kbss.termit.util.TypeAwareResource;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static cz.cvut.kbss.termit.util.Constants.Excel.FILE_EXTENSION;
import static cz.cvut.kbss.termit.util.Constants.Excel.MEDIA_TYPE;

/**
 * Supports vocabulary export to MS Excel format
 */
@Service("excel")
public class ExcelVocabularyExporter implements VocabularyExporter {

    /**
     * Name of the single sheet produced by this exporter
     */
    static final String SHEET_NAME = "Glossary";

    private final TermRepositoryService termService;

    @Autowired
    public ExcelVocabularyExporter(TermRepositoryService termService) {
        this.termService = termService;
    }

    @Override
    public TypeAwareResource exportGlossary(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        try (final XSSFWorkbook wb = new XSSFWorkbook()) {
            final Sheet sheet = wb.createSheet(SHEET_NAME);
            generateHeaderRow(sheet);
            generateTermRows(termService.findAll(vocabulary), sheet);
            final ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return new TypeAwareByteArrayResource(bos.toByteArray(), MEDIA_TYPE, FILE_EXTENSION);
        } catch (IOException e) {
            throw new TermItException("Unable to generate excel file from glossary of " + vocabulary, e);
        }
    }

    private static void generateHeaderRow(Sheet sheet) {
        final Row row = sheet.createRow(0);
        for (int i = 0; i < Term.EXPORT_COLUMNS.size(); i++) {
            row.createCell(i).setCellValue(Term.EXPORT_COLUMNS.get(i));
        }
    }

    private static void generateTermRows(List<Term> terms, Sheet sheet) {
        // Row no. 0 is the header
        for (int i = 0; i < terms.size(); i++) {
            final Row row = sheet.createRow(i + 1);
            terms.get(i).toExcel(row);
        }
    }

    @Override
    public TypeAwareResource exportGlossaryWithReferences(Vocabulary vocabulary,
                                                          Collection<String> properties) {
        throw new UnsupportedOperationException("Exporting glossary with references to Excel is not supported.");
    }

    @Override
    public boolean supports(String mediaType) {
        return Objects.equals(MEDIA_TYPE, mediaType);
    }
}
