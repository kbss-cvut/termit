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

import cz.cvut.kbss.termit.dto.PrefixDeclaration;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.exception.TermItException;
import cz.cvut.kbss.termit.exception.UnsupportedOperationException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.service.business.VocabularyService;
import cz.cvut.kbss.termit.service.export.util.TypeAwareByteArrayResource;
import cz.cvut.kbss.termit.service.repository.TermRepositoryService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.TypeAwareResource;
import cz.cvut.kbss.termit.util.Utils;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implements vocabulary export to the MS Excel format.
 */
@Service("excel")
public class ExcelVocabularyExporter implements VocabularyExporter {

    private static final Logger LOG = LoggerFactory.getLogger(ExcelVocabularyExporter.class);

    /**
     * Index of the main sheet containing the exported glossary
     */
    static final int GLOSSARY_SHEET_INDEX = 0;
    /**
     * Index of the sheet with prefix mapping
     */
    static final int PREFIX_SHEET_INDEX = 1;
    /**
     * Name of the prefix column in the prefix mapping sheet
     */
    static final String PREFIX_COLUMN = "Prefix";
    /**
     * Name of the namespace column in the prefix mapping sheet
     */
    static final String NAMESPACE_COLUMN = "Namespace";

    private static final String FONT = "Arial";
    private static final short FONT_SIZE = (short) 10;

    private final TermRepositoryService termService;

    private final VocabularyService vocabularyService;

    private final Configuration config;

    @Autowired
    public ExcelVocabularyExporter(TermRepositoryService termService, VocabularyService vocabularyService,
                                   Configuration config) {
        this.termService = termService;
        this.vocabularyService = vocabularyService;
        this.config = config;
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
        try (final XSSFWorkbook wb = new XSSFWorkbook(loadWorkbookTemplate())) {
            final Map<URI, PrefixDeclaration> prefixes = new HashMap<>();
            generateGlossarySheet(vocabulary, wb, prefixes);
            generatePrefixMappingSheet(wb, prefixes);
            final ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return new TypeAwareByteArrayResource(bos.toByteArray(), ExportFormat.EXCEL.getMediaType(),
                                                  ExportFormat.EXCEL.getFileExtension());
        } catch (IOException e) {
            throw new TermItException("Unable to generate excel file from glossary of " + vocabulary, e);
        }
    }

    private InputStream loadWorkbookTemplate() {
        final InputStream templateIs = ExcelVocabularyExporter.class.getClassLoader().getResourceAsStream(
                "template/" + config.getPersistence().getLanguage() + "/export.xlsx");
        if (templateIs == null) {
            LOG.warn("Localized Excel export template file not found. Falling back to the default one.");
            return ExcelVocabularyExporter.class.getClassLoader().getResourceAsStream(
                    "template/" + Constants.DEFAULT_LANGUAGE + "/export.xlsx");
        }
        return templateIs;
    }

    private void generateGlossarySheet(Vocabulary vocabulary, XSSFWorkbook wb, Map<URI, PrefixDeclaration> prefixes) {
        final Sheet sheet = wb.getSheetAt(GLOSSARY_SHEET_INDEX);
        generateTermRows(termService.findAllFull(vocabulary), wb, sheet, prefixes);
    }

    private static XSSFFont initFont(XSSFWorkbook wb) {
        final XSSFFont font = wb.createFont();
        font.setFontHeightInPoints(FONT_SIZE);
        font.setFontName(FONT);
        return font;
    }

    private void generateTermRows(List<Term> terms, XSSFWorkbook wb, Sheet sheet,
                                  Map<URI, PrefixDeclaration> prefixes) {
        final XSSFFont font = initFont(wb);
        final CellStyle style = wb.createCellStyle();
        style.setFont(font);
        style.setWrapText(true);
        final ExcelTermExporter termExporter = new ExcelTermExporter(prefixes);
        for (int i = 0; i < terms.size(); i++) {
            // Row no. 0 is the header
            final Row row = sheet.createRow(i + 1);
            row.setRowStyle(style);
            final Term t = terms.get(i);
            resolvePrefixes(t, prefixes);
            termExporter.export(t, row);
        }
    }

    private void resolvePrefixes(Term t, Map<URI, PrefixDeclaration> prefixes) {
        if (!prefixes.containsKey(t.getVocabulary())) {
            prefixes.put(t.getVocabulary(), vocabularyService.resolvePrefix(t.getVocabulary()));
        }
        final Set<TermInfo> allRelated = new HashSet<>();
        allRelated.addAll(Utils.emptyIfNull(t.getSubTerms()));
        allRelated.addAll(Utils.emptyIfNull(t.getExactMatchTerms()));
        allRelated.addAll(Utils.emptyIfNull(t.getInverseExactMatchTerms()));
        allRelated.addAll(Utils.emptyIfNull(t.getRelatedMatch()));
        allRelated.addAll(Utils.emptyIfNull(t.getInverseRelatedMatch()));
        allRelated.addAll(
                Utils.emptyIfNull(t.getExternalParentTerms()).stream().map(TermInfo::new).collect(Collectors.toSet()));
        allRelated.stream().filter(ti -> !prefixes.containsKey(ti.getVocabulary()))
                  .forEach(ti -> prefixes.put(ti.getVocabulary(), vocabularyService.resolvePrefix(ti.getVocabulary())));
    }

    private void generatePrefixMappingSheet(XSSFWorkbook wb, Map<URI, PrefixDeclaration> prefixes) {
        final Sheet sheet = wb.getSheetAt(PREFIX_SHEET_INDEX);
        final XSSFFont font = initFont(wb);
        final CellStyle style = wb.createCellStyle();
        style.setFont(font);
        style.setWrapText(true);
        // Prefixes
        int i = 1;
        for (PrefixDeclaration pd : prefixes.values()) {
            if (pd.getPrefix() == null) {
                continue;
            }
            final Row prefixRow = sheet.createRow(i++);
            prefixRow.setRowStyle(style);
            prefixRow.createCell(0).setCellValue(pd.getPrefix());
            prefixRow.createCell(1).setCellValue(pd.getNamespace());
        }
    }

    @Override
    public boolean supports(String mediaType) {
        return Objects.equals(ExportFormat.EXCEL.getMediaType(), mediaType);
    }
}
