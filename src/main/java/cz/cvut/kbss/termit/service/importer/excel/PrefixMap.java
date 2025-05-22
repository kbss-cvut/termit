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
