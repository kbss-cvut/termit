/*
 * TermIt
 * Copyright (C) 2023 Czech Technical University in Prague
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
package cz.cvut.kbss.termit.rest.doc;

/**
 * Common constants for the Open API documentation of the system REST API.
 */
public class ApiDocConstants {

    /**
     * Description of the {@link cz.cvut.kbss.termit.util.Constants.QueryParams#PAGE_SIZE} query parameter.
     */
    public static final String PAGE_SIZE_DESCRIPTION = "Number of items to retrieve.";

    /**
     * Description of the {@link cz.cvut.kbss.termit.util.Constants.QueryParams#PAGE} query parameter.
     */
    public static final String PAGE_NO_DESCRIPTION = "Page number.";

    /**
     * Example of a ISO-formatted datetime accepted by the API when specifying datetime range.
     */
    public static final String DATETIME_EXAMPLE = "2023-01-01T00:00:00";

    private ApiDocConstants() {
        throw new AssertionError();
    }
}
