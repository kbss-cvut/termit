package cz.cvut.kbss.termit.service.importer.excel;

import cz.cvut.kbss.termit.service.export.ExcelVocabularyExporter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import java.util.HashMap;
import java.util.Map;

class PrefixMap {

    private final Map<String, String> prefixMap = new HashMap<>();

    PrefixMap() {
    }

    PrefixMap(Sheet prefixSheet) {
        for (Row row : prefixSheet) {
            if (row.getRowNum() == 0) {
                continue;
            }

            if (row.getCell(ExcelVocabularyExporter.PREFIX_COLUMN_NUMBER) == null || row.getCell(
                    ExcelVocabularyExporter.PREFIX_COLUMN_NUMBER).getStringCellValue().isBlank()) {
                return;
            }
            final String prefix = row.getCell(ExcelVocabularyExporter.PREFIX_COLUMN_NUMBER).getStringCellValue();
            final String uri = row.getCell(ExcelVocabularyExporter.NAMESPACE_COLUMN_NUMBER).getStringCellValue();
            prefixMap.put(prefix, uri);
        }
    }

    String resolvePrefixed(String value) {
        final int colonIndex = value.indexOf(':');
        if (colonIndex > 0) {
            final String prefix = value.substring(0, colonIndex);
            return prefixMap.containsKey(prefix) ? (prefixMap.get(prefix) + value.substring(colonIndex + 1)) : value;
        } else {
            return value;
        }
    }

    @Override
    public String toString() {
        return "PrefixMap{" + prefixMap + '}';
    }
}
