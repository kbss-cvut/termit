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
package cz.cvut.kbss.termit.service.export.util;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.termit.util.CsvUtils;

import java.util.function.Function;
import java.util.stream.Collectors;

public class TabularTermExportUtils {

    /**
     * Delimiter of a joined list of strings.
     */
    public static final String STRING_DELIMITER = ";";

    private TabularTermExportUtils() {
        throw new AssertionError();
    }

    // TODO Remove
    /**
     * Transforms the specified {@link MultilingualString} to a single string where individual translations are
     * separated by a predefined delimiter.
     * <p>
     * The translations are added to the result in the following form: {@literal translation(language)}.
     *
     * @param str            Multilingual string to transform
     * @param preProcessor   Function to apply to every translation before it is added to the result
     * @param sanitizeCommas Whether to sanitize commas in the string content
     * @return A single string containing all translations from the argument
     */
    public static String exportMultilingualString(MultilingualString str, Function<String, String> preProcessor,
                                                  boolean sanitizeCommas) {
        if (str == null) {
            return "";
        }
        return String.join(STRING_DELIMITER,
                           str.getValue().entrySet().stream()
                              .map(e -> (sanitizeCommas ? CsvUtils.sanitizeString(preProcessor.apply(e.getValue())) :
                                         preProcessor.apply(e.getValue())) + "(" + e.getKey() + ")")
                              .collect(Collectors.toSet()));
    }
}
