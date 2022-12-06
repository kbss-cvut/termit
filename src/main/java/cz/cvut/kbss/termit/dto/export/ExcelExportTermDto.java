package cz.cvut.kbss.termit.dto.export;

import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.util.Utils;
import org.apache.poi.ss.usermodel.Row;

import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Term representation that can export itself to an Excel row.
 */
public class ExcelExportTermDto extends Term {

    public void export(Row row) {
        Objects.requireNonNull(row);
        row.createCell(0).setCellValue(getUri().toString());
        row.createCell(1).setCellValue(TabularTermExportUtils.exportMultilingualString(getLabel(), false));
        row.createCell(2)
           .setCellValue(TabularTermExportUtils.exportStringCollection(
                   Utils.emptyIfNull(getAltLabels()).stream()
                        .map(str -> TabularTermExportUtils.exportMultilingualString(str, false))
                        .collect(Collectors.toSet())));
        row.createCell(3)
           .setCellValue(TabularTermExportUtils.exportStringCollection(
                   Utils.emptyIfNull(getHiddenLabels()).stream()
                        .map(str -> TabularTermExportUtils.exportMultilingualString(str, false))
                        .collect(Collectors.toSet())));
        row.createCell(4).setCellValue(TabularTermExportUtils.exportMultilingualString(getDefinition(), false));

        row.createCell(5).setCellValue(TabularTermExportUtils.exportMultilingualString(getDescription(), false));
        row.createCell(6).setCellValue(TabularTermExportUtils.exportStringCollection(Utils.emptyIfNull(getTypes())));
        row.createCell(7).setCellValue(TabularTermExportUtils.exportStringCollection(Utils.emptyIfNull(getSources())));
        row.createCell(8)
           .setCellValue(TabularTermExportUtils.exportStringCollection(
                   Utils.emptyIfNull(getParentTerms()).stream().map(pt -> pt.getUri().toString())
                        .collect(Collectors.toSet())));
        row.createCell(9)
           .setCellValue(TabularTermExportUtils.exportStringCollection(
                   Utils.emptyIfNull(getSubTerms()).stream().map(TabularTermExportUtils::termInfoStringIri)
                        .collect(Collectors.toSet())));
        row.createCell(10).setCellValue(TabularTermExportUtils.exportStringCollection(
                Utils.joinCollections(getRelated(), getInverseRelated()).stream()
                     .map(TabularTermExportUtils::termInfoStringIri)
                     .distinct()
                     .collect(Collectors.toList())));
        row.createCell(11)
           .setCellValue(TabularTermExportUtils.exportStringCollection(
                   Utils.joinCollections(getRelatedMatch(), getInverseRelatedMatch()).stream()
                        .map(TabularTermExportUtils::termInfoStringIri)
                        .distinct()
                        .collect(Collectors.toList())));
        row.createCell(12)
           .setCellValue(TabularTermExportUtils.exportStringCollection(
                   Utils.joinCollections(getExactMatchTerms(), getInverseExactMatchTerms()).stream()
                        .map(TabularTermExportUtils::termInfoStringIri)
                        .distinct()
                        .collect(Collectors.toList())));
        row.createCell(13).setCellValue(TabularTermExportUtils.draftToStatus(this));
        row.createCell(14)
           .setCellValue(TabularTermExportUtils.exportStringCollection(Utils.emptyIfNull(getNotations())));
        row.createCell(15).setCellValue(TabularTermExportUtils.exportStringCollection(
                Utils.emptyIfNull(getExamples()).stream()
                     .map(str -> TabularTermExportUtils.exportMultilingualString(str, false))
                     .collect(Collectors.toSet())));
        if (getProperties() != null && !Utils.emptyIfNull(getProperties().get(DC.Terms.REFERENCES)).isEmpty()) {
            row.createCell(16).setCellValue(getProperties().get(DC.Terms.REFERENCES).toString());
        }
    }
}
