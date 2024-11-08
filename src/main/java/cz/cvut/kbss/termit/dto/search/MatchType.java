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
package cz.cvut.kbss.termit.dto.search;

/**
 * Describes how the property value should be matched in the data.
 */
public enum MatchType {
    /**
     * Matches resource identifier in the repository.
     */
    IRI,
    /**
     * Matches the specified value as a substring of the string representation of a property value in the repository.
     * <p>
     * Note that this match is not case-sensitive.
     */
    SUBSTRING,
    /**
     * Matches the specified value exactly to the string representation of a property value in the repository.
     */
    EXACT_MATCH
}
