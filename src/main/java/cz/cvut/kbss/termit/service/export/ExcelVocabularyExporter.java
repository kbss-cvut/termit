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
import cz.cvut.kbss.termit.exception.TermItException;
import cz.cvut.kbss.termit.exception.UnsupportedOperationException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.service.business.VocabularyService;
import cz.cvut.kbss.termit.service.export.util.TypeAwareByteArrayResource;
import cz.cvut.kbss.termit.service.repository.TermRepositoryService;
import cz.cvut.kbss.termit.service.repository.VocabularyRepositoryService;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.TypeAwareResource;
import cz.cvut.kbss.termit.util.Utils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implements vocabulary export to the MS Excel format.
 */
@Service("excel")
public class ExcelVocabularyExporter implements VocabularyExporter {
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
    private static final int COLUMN_WIDTH = 20;

    private final TermRepositoryService termService;

    private final VocabularyRepositoryService vocabularyService;

    @Autowired
    public ExcelVocabularyExporter(TermRepositoryService termService, VocabularyRepositoryService vocabularyService) {
        this.termService = termService;
        this.vocabularyService = vocabularyService;
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
            final Map<URI, PrefixDeclaration> prefixes = new HashMap<>();
            generateGlossarySheets(vocabulary, wb, prefixes);
            generatePrefixMappingSheet(wb, prefixes.values());
            final ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return new TypeAwareByteArrayResource(bos.toByteArray(), ExportFormat.EXCEL.getMediaType(),
                                                  ExportFormat.EXCEL.getFileExtension());
        } catch (IOException e) {
            throw new TermItException("Unable to generate excel file from glossary of " + vocabulary, e);
        }
    }

    private void generateGlossarySheets(Vocabulary vocabulary, XSSFWorkbook wb, Map<URI, PrefixDeclaration> prefixes) {
        final List<Term> terms = termService.findAllFull(vocabulary);
        final List<String> uniqueLangCodes = extractUniqueLanguages(terms);
        uniqueLangCodes.forEach(langCode -> {
            final LanguageCode lang = LanguageCode.getByCodeIgnoreCase(langCode);
            final XSSFSheet sheet = wb.createSheet(lang != null ? lang.getName() : langCode);
            generateHeader(sheet, langCode);
            generateTermRows(terms, sheet, langCode, prefixes);
        });
    }

    private List<String> extractUniqueLanguages(List<Term> terms) {
        final Set<String> uniqueLanguages = new HashSet<>();
        for (Term t : terms) {
            uniqueLanguages.addAll(t.getLabel().getLanguages());
            uniqueLanguages.addAll(t.getDefinition() != null ? t.getDefinition().getLanguages() : Collections.emptyList());
            uniqueLanguages.addAll(t.getDescription() != null ? t.getDescription().getLanguages() : Collections.emptyList());
            Utils.emptyIfNull(t.getAltLabels()).forEach(ms -> uniqueLanguages.addAll(ms.getLanguages()));
            Utils.emptyIfNull(t.getHiddenLabels()).forEach(ms -> uniqueLanguages.addAll(ms.getLanguages()));
            Utils.emptyIfNull(t.getExamples()).forEach(ms -> uniqueLanguages.addAll(ms.getLanguages()));
        }
        return uniqueLanguages.stream().filter(Objects::nonNull).sorted().collect(Collectors.toList());
    }

    private static XSSFFont initFont(XSSFWorkbook wb) {
        final XSSFFont font = wb.createFont();
        font.setFontHeightInPoints(FONT_SIZE);
        font.setFontName(FONT);
        return font;
    }

    private void generateHeader(XSSFSheet sheet, String langCode) {
        final List<String> columns = Constants.EXPORT_COLUMN_LABELS.getOrDefault(langCode,
                                                                                 Constants.EXPORT_COLUMN_LABELS.get(
                                                                                         Constants.DEFAULT_LANGUAGE));
        final Row row = generateHeaderRow(sheet);
        for (int i = 0; i < columns.size(); i++) {
            sheet.setColumnWidth(i, COLUMN_WIDTH * 256);
            final Cell cell = row.createCell(i);
            cell.setCellValue(columns.get(i));
        }
    }

    private Row generateHeaderRow(XSSFSheet sheet) {
        final XSSFFont font = initFont(sheet.getWorkbook());
        font.setBold(true);
        final CellStyle cellStyle = sheet.getWorkbook().createCellStyle();
        cellStyle.setFont(font);
        final Row row = sheet.createRow(0);
        row.setRowStyle(cellStyle);
        return row;
    }

    private void generateTermRows(List<Term> terms, XSSFSheet sheet, String langCode,
                                  Map<URI, PrefixDeclaration> prefixes) {
        final XSSFFont font = initFont(sheet.getWorkbook());
        final CellStyle style = sheet.getWorkbook().createCellStyle();
        style.setFont(font);
        style.setWrapText(true);
        final ExcelTermExporter termExporter = new ExcelTermExporter(prefixes, langCode);
        for (int i = 0; i < terms.size(); i++) {
            // Row no. 0 is the header
            final Row row = sheet.createRow(i + 1);
            row.setRowStyle(style);
            final Term t = terms.get(i);
            resolvePrefixes(t, prefixes);
            termExporter.export(t, row);
            for (short j = 0; j < row.getLastCellNum(); j++) {
                row.getCell(j).setCellStyle(style);
            }
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

    private void generatePrefixMappingSheet(XSSFWorkbook wb, Collection<PrefixDeclaration> prefixes) {
        final XSSFSheet sheet = wb.createSheet(PREFIX_COLUMN);
        generatePrefixSheetHeader(sheet);
        final XSSFFont font = initFont(wb);
        final CellStyle style = wb.createCellStyle();
        style.setFont(font);
        style.setWrapText(true);
        // Prefixes
        int i = 1;
        final List<PrefixDeclaration> prefixList = new ArrayList<>(prefixes);
        Collections.sort(prefixList);
        for (PrefixDeclaration pd : prefixList) {
            if (pd.getPrefix() == null) {
                continue;
            }
            final Row prefixRow = sheet.createRow(i++);
            prefixRow.setRowStyle(style);
            prefixRow.createCell(0).setCellValue(pd.getPrefix());
            prefixRow.createCell(1).setCellValue(pd.getNamespace());
        }
    }

    private void generatePrefixSheetHeader(XSSFSheet sheet) {
        final Row row = generateHeaderRow(sheet);
        row.createCell(0).setCellValue(PREFIX_COLUMN);
        sheet.setColumnWidth(0, COLUMN_WIDTH * 2 * 256);
        row.createCell(1).setCellValue(NAMESPACE_COLUMN);
        sheet.setColumnWidth(1, COLUMN_WIDTH * 2 * 256);
    }

    @Override
    public boolean supports(String mediaType) {
        return Objects.equals(ExportFormat.EXCEL.getMediaType(), mediaType);
    }
}
