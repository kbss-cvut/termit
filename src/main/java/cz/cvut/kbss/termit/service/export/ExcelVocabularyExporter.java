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

import cz.cvut.kbss.termit.exception.TermItException;
import cz.cvut.kbss.termit.exception.UnsupportedOperationException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.service.export.util.TabularTermExportUtils;
import cz.cvut.kbss.termit.service.export.util.TypeAwareByteArrayResource;
import cz.cvut.kbss.termit.service.repository.TermRepositoryService;
import cz.cvut.kbss.termit.util.TypeAwareResource;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Implements vocabulary export to the MS Excel format.
 */
@Service("excel")
public class ExcelVocabularyExporter implements VocabularyExporter {

    /**
     * Name of the single sheet produced by this exporter
     */
    static final String SHEET_NAME = "Glossary";
    private static final String FONT = "Arial";
    private static final short FONT_SIZE = (short) 10;
    private static final int COLUMN_WIDTH = 25;

    private final TermRepositoryService termService;

    @Autowired
    public ExcelVocabularyExporter(TermRepositoryService termService) {
        this.termService = termService;
    }

    @Override
    public TypeAwareResource exportGlossary(Vocabulary vocabulary, ExportConfig config) {
        Objects.requireNonNull(vocabulary);
        Objects.requireNonNull(config);
        if (ExportType.SKOS == config.getType()) {
            return exportGlossary(vocabulary);
        }
        throw new UnsupportedOperationException("Unsupported export type " + config.getType());
    }

    private TypeAwareResource exportGlossary(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        try (final XSSFWorkbook wb = new XSSFWorkbook()) {
            final Sheet sheet = wb.createSheet(SHEET_NAME);
            generateHeaderRow(wb, sheet);
            generateTermRows(termService.findAllFull(vocabulary), wb, sheet);
            final ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return new TypeAwareByteArrayResource(bos.toByteArray(), ExportFormat.EXCEL.getMediaType(),
                                                  ExportFormat.EXCEL.getFileExtension());
        } catch (IOException e) {
            throw new TermItException("Unable to generate excel file from glossary of " + vocabulary, e);
        }
    }

    private static void generateHeaderRow(XSSFWorkbook wb, Sheet sheet) {
        final XSSFFont font = initFont(wb);
        font.setBold(true);
        final Row row = sheet.createRow(0);
        final CellStyle style = wb.createCellStyle();
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        row.setRowStyle(style);
        for (int i = 0; i < TabularTermExportUtils.EXPORT_COLUMNS.size(); i++) {
            sheet.setColumnWidth(i, COLUMN_WIDTH * 256);
            row.createCell(i).setCellValue(TabularTermExportUtils.EXPORT_COLUMNS.get(i));

        }
    }

    private static XSSFFont initFont(XSSFWorkbook wb) {
        final XSSFFont font = wb.createFont();
        font.setFontHeightInPoints(FONT_SIZE);
        font.setFontName(FONT);
        return font;
    }

    private void generateTermRows(List<Term> terms, XSSFWorkbook wb, Sheet sheet) {
        final XSSFFont font = initFont(wb);
        final CellStyle style = wb.createCellStyle();
        style.setFont(font);
        style.setWrapText(true);
        final ExcelTermExporter termExporter = new ExcelTermExporter();
        for (int i = 0; i < terms.size(); i++) {
            // Row no. 0 is the header
            final Row row = sheet.createRow(i + 1);
            row.setRowStyle(style);
            termExporter.export(terms.get(i), row);
        }
    }

    @Override
    public boolean supports(String mediaType) {
        return Objects.equals(ExportFormat.EXCEL.getMediaType(), mediaType);
    }
}
