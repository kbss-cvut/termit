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
package cz.cvut.kbss.termit.dto.search;

import cz.cvut.kbss.termit.exception.ValidationException;
import cz.cvut.kbss.termit.util.Utils;

import java.net.URI;
import java.util.Objects;
import java.util.Set;

/**
 * Parameter of the faceted term search.
 */
public class SearchParam {

    private URI property;

    private Set<Object> value;

    private MatchType matchType = MatchType.EXACT_MATCH;

    public SearchParam() {
    }

    // For test purposes
    public SearchParam(URI property, Set<Object> value, MatchType matchType) {
        this.property = Objects.requireNonNull(property);
        this.value = value;
        this.matchType = Objects.requireNonNull(matchType);
    }

    public URI getProperty() {
        return property;
    }

    public void setProperty(URI property) {
        this.property = property;
    }

    public Set<Object> getValue() {
        return value;
    }

    public void setValue(Set<Object> value) {
        this.value = value;
    }

    public MatchType getMatchType() {
        return matchType;
    }

    public void setMatchType(MatchType matchType) {
        this.matchType = matchType;
    }

    /**
     * Validates this search parameter.
     * <p>
     * This mainly means checking that the values correspond to the match type, e.g., that a single value is provided
     * for string-matching types.
     */
    public void validate() {
        if (Utils.emptyIfNull(value).isEmpty() || property == null) {
            throw new ValidationException("Must provide a property and value to search by!");
        }
        if (matchType != MatchType.IRI && Utils.emptyIfNull(value).size() != 1) {
            throw new ValidationException("Exactly one value must be provided for match type " + matchType);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SearchParam that)) {
            return false;
        }
        return Objects.equals(property, that.property)
                && Objects.equals(value, that.value) && matchType == that.matchType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(property, value, matchType);
    }

    @Override
    public String toString() {
        return "SearchParam{" +
                "property=" + Utils.uriToString(property) +
                ", value='" + value + '\'' +
                ", matchType=" + matchType +
                '}';
    }
}
