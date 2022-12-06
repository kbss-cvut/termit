package cz.cvut.kbss.termit.service.export;

import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.service.export.util.TabularTermExportUtils;
import cz.cvut.kbss.termit.util.Utils;
import org.apache.poi.ss.usermodel.Row;

import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Term representation that can export itself to an Excel row.
 */
public class ExcelTermExporter {

    public void export(Term t, Row row) {
        Objects.requireNonNull(row);
        row.createCell(0).setCellValue(t.getUri().toString());
        row.createCell(1)
           .setCellValue(TabularTermExportUtils.exportMultilingualString(t.getLabel(), Function.identity(), false));
        row.createCell(2)
           .setCellValue(String.join(TabularTermExportUtils.STRING_DELIMITER,
                                     Utils.emptyIfNull(t.getAltLabels()).stream()
                                          .map(str -> TabularTermExportUtils.exportMultilingualString(str,
                                                                                                      Function.identity(),
                                                                                                      false))
                                          .collect(Collectors.toSet())));
        row.createCell(3)
           .setCellValue(String.join(TabularTermExportUtils.STRING_DELIMITER,
                                     Utils.emptyIfNull(t.getHiddenLabels()).stream()
                                          .map(str -> TabularTermExportUtils.exportMultilingualString(str,
                                                                                                      Function.identity(),
                                                                                                      false))
                                          .collect(Collectors.toSet())));
        row.createCell(4).setCellValue(
                TabularTermExportUtils.exportMultilingualString(t.getDefinition(), Utils::markdownToPlainText, false));
        row.createCell(5).setCellValue(
                TabularTermExportUtils.exportMultilingualString(t.getDescription(), Utils::markdownToPlainText, false));
        row.createCell(6)
           .setCellValue(String.join(TabularTermExportUtils.STRING_DELIMITER, Utils.emptyIfNull(t.getTypes())));
        row.createCell(7)
           .setCellValue(String.join(TabularTermExportUtils.STRING_DELIMITER, Utils.emptyIfNull(t.getSources())));
        row.createCell(8)
           .setCellValue(Utils.emptyIfNull(t.getParentTerms()).stream().map(pt -> pt.getUri().toString())
                              .collect(Collectors.joining(TabularTermExportUtils.STRING_DELIMITER)));
        row.createCell(9)
           .setCellValue(String.join(TabularTermExportUtils.STRING_DELIMITER,
                                     Utils.emptyIfNull(t.getSubTerms()).stream()
                                          .map(TabularTermExportUtils::termInfoStringIri)
                                          .collect(Collectors.toSet())));
        row.createCell(10).setCellValue(Utils.joinCollections(t.getRelated(), t.getInverseRelated()).stream()
                                             .map(TabularTermExportUtils::termInfoStringIri)
                                             .distinct()
                                             .collect(Collectors.joining(TabularTermExportUtils.STRING_DELIMITER)));
        row.createCell(11)
           .setCellValue(Utils.joinCollections(t.getRelatedMatch(), t.getInverseRelatedMatch()).stream()
                              .map(TabularTermExportUtils::termInfoStringIri)
                              .distinct()
                              .collect(Collectors.joining(TabularTermExportUtils.STRING_DELIMITER)));
        row.createCell(12)
           .setCellValue(Utils.joinCollections(t.getExactMatchTerms(), t.getInverseExactMatchTerms()).stream()
                              .map(TabularTermExportUtils::termInfoStringIri)
                              .distinct()
                              .collect(Collectors.joining(TabularTermExportUtils.STRING_DELIMITER)));
        row.createCell(13).setCellValue(TabularTermExportUtils.draftToStatus(t));
        row.createCell(14)
           .setCellValue(String.join(TabularTermExportUtils.STRING_DELIMITER, Utils.emptyIfNull(t.getNotations())));
        row.createCell(15).setCellValue(Utils.emptyIfNull(t.getExamples()).stream()
                                             .map(str -> TabularTermExportUtils.exportMultilingualString(str,
                                                                                                         Function.identity(),
                                                                                                         false))
                                             .collect(Collectors.joining(TabularTermExportUtils.STRING_DELIMITER)));
        if (t.getProperties() != null && !Utils.emptyIfNull(t.getProperties().get(DC.Terms.REFERENCES)).isEmpty()) {
            row.createCell(16).setCellValue(t.getProperties().get(DC.Terms.REFERENCES).toString());
        }
    }
}
