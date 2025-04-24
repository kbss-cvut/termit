/*
 * TermIt
 * Copyright (C) 2025 Czech Technical University in Prague
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

import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.termit.dto.PrefixDeclaration;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.service.export.util.TabularTermExportUtils;
import cz.cvut.kbss.termit.util.Utils;
import org.apache.poi.ss.usermodel.Row;

import java.net.URI;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Term representation that can export itself to an Excel row.
 */
public class ExcelTermExporter {

    private final Map<URI, PrefixDeclaration> prefixes;
    private final String langCode;

    public ExcelTermExporter(Map<URI, PrefixDeclaration> prefixes, String langCode) {
        this.prefixes = prefixes;
        this.langCode = langCode;
    }

    public void export(Term t, Row row) {
        Objects.requireNonNull(row);
        row.createCell(0).setCellValue(prefixedUri(t.getVocabulary(), t));
        row.createCell(1).setCellValue(t.getLabel().get(langCode));
        row.createCell(2).setCellValue(Utils.emptyIfNull(t.getAltLabels()).stream()
                                            .map(str -> str.get(langCode))
                                            .filter(Objects::nonNull)
                                            .collect(Collectors.joining(TabularTermExportUtils.STRING_DELIMITER)));
        row.createCell(3).setCellValue(Utils.emptyIfNull(t.getHiddenLabels()).stream()
                                            .map(str -> str.get(langCode))
                                            .filter(Objects::nonNull)
                                            .collect(Collectors.joining(TabularTermExportUtils.STRING_DELIMITER)));
        row.createCell(4)
           .setCellValue(Utils.markdownToPlainText(t.getDefinition() != null ? t.getDefinition().get(langCode) : null));
        row.createCell(5).setCellValue(
                Utils.markdownToPlainText(t.getDescription() != null ? t.getDescription().get(langCode) : null));
        row.createCell(6)
           .setCellValue(String.join(TabularTermExportUtils.STRING_DELIMITER, Utils.emptyIfNull(t.getTypes())));
        row.createCell(7)
           .setCellValue(String.join(TabularTermExportUtils.STRING_DELIMITER, Utils.emptyIfNull(t.getSources())));
        row.createCell(8)
           .setCellValue(Utils.emptyIfNull(t.getParentTerms()).stream().map(pt -> prefixedUri(pt.getVocabulary(), pt))
                              .collect(Collectors.joining(TabularTermExportUtils.STRING_DELIMITER)));
        row.createCell(9)
           .setCellValue(Utils.emptyIfNull(t.getSubTerms()).stream()
                              .map(this::termInfoPrefixedUri)
                              .collect(Collectors.joining(TabularTermExportUtils.STRING_DELIMITER)));
        row.createCell(10).setCellValue(Utils.joinCollections(t.getRelated(), t.getInverseRelated()).stream()
                                             .map(this::termInfoPrefixedUri)
                                             .distinct()
                                             .collect(Collectors.joining(TabularTermExportUtils.STRING_DELIMITER)));
        row.createCell(11)
           .setCellValue(Utils.joinCollections(t.getRelatedMatch(), t.getInverseRelatedMatch()).stream()
                              .map(this::termInfoPrefixedUri)
                              .distinct()
                              .collect(Collectors.joining(TabularTermExportUtils.STRING_DELIMITER)));
        row.createCell(12)
           .setCellValue(Utils.joinCollections(t.getExactMatchTerms(), t.getInverseExactMatchTerms()).stream()
                              .map(this::termInfoPrefixedUri)
                              .distinct()
                              .collect(Collectors.joining(TabularTermExportUtils.STRING_DELIMITER)));
        row.createCell(13).setCellValue(t.getState() != null ? t.getState().toString() : "");
        row.createCell(14)
           .setCellValue(String.join(TabularTermExportUtils.STRING_DELIMITER, Utils.emptyIfNull(t.getNotations())));
        row.createCell(15).setCellValue(Utils.emptyIfNull(t.getExamples()).stream()
                                             .map(str -> str.get(langCode))
                                             .filter(Objects::nonNull)
                                             .collect(Collectors.joining(TabularTermExportUtils.STRING_DELIMITER)));
        if (t.getProperties() != null && !Utils.emptyIfNull(t.getProperties().get(DC.Terms.REFERENCES)).isEmpty()) {
            row.createCell(16).setCellValue(t.getProperties().get(DC.Terms.REFERENCES).toString());
        }
    }

    private String termInfoPrefixedUri(TermInfo ti) {
        return prefixedUri(ti.getVocabulary(), ti);
    }

    private String prefixedUri(URI vocabularyUri, HasIdentifier asset) {
        final String strUri = asset.getUri().toString();
        if (!prefixes.containsKey(vocabularyUri)) {
            return strUri;
        }
        final PrefixDeclaration prefix = prefixes.get(vocabularyUri);
        if (prefix.getNamespace() != null && strUri.startsWith(prefix.getNamespace())) {
            return prefix.getPrefix() + PrefixDeclaration.SEPARATOR + strUri.substring(prefix.getNamespace().length());
        }
        return strUri;
    }
}
