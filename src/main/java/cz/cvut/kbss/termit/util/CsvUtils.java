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
package cz.cvut.kbss.termit.util;

/**
 * Utilities for handling CSV files.
 */
public class CsvUtils {

    private CsvUtils() {
        throw new AssertionError();
    }

    /**
     * Sanitizes string which should be a part of a CSV file.
     * <p>
     * This means that if the string contains commas, it is enclosed in double quotes (") and if it contains double
     * quotes, they are doubled, i.e., "test" becomes ""test"".
     *
     * @param str The string to sanitize
     * @return Sanitized string
     */
    public static String sanitizeString(String str) {
        if (str == null) {
            return "";
        }
        String result = str;
        boolean sanitized = result.contains(",") || result.contains("\n");
        if (result.contains("\"")) {
            sanitized = true;
            result = result.replace("\"", "\"\"");
        }
        return sanitized ? "\"" + result + '\"' : result;
    }
}
