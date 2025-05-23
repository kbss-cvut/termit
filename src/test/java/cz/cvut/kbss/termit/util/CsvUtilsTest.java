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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvUtilsTest {

    @Test
    void sanitizeStringReturnsEmptyStringIfNullIsPassedAsArgument() {
        assertEquals("", CsvUtils.sanitizeString(null));
    }

    @Test
    void sanitizeStringReturnsInputWhenNothingNeedsSanitization() {
        final String input = "This string needs no sanitization.";
        assertEquals(input, CsvUtils.sanitizeString(input));
    }

    @Test
    void sanitizeStringEnclosesStringInQuotesWhenItContainsCommas() {
        final String input = "string, which contains comma";
        final String result = CsvUtils.sanitizeString(input);
        assertEquals("\"" + input + "\"", result);
    }

    @Test
    void sanitizeStringDuplicatesDoubleQuotesInString() {
        final String input = "This string needs \"escaping\"";
        final String result = CsvUtils.sanitizeString(input);
        assertEquals("\"This string needs \"\"escaping\"\"\"", result);
    }
}
