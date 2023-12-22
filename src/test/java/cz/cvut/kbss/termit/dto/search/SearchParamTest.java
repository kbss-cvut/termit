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

import cz.cvut.kbss.jopa.vocabulary.RDF;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SearchParamTest {

    @Test
    void validateThrowsValidationExceptionWhenMatchTypeIsSubStringAndParamsHasMultipleValues() {
        final SearchParam sut = new SearchParam();
        sut.setProperty(URI.create(SKOS.NOTATION));
        sut.setValue(Set.of("one", "two", "three"));
        sut.setMatchType(MatchType.SUBSTRING);
        assertThrows(ValidationException.class, sut::validate);
    }

    @Test
    void validateThrowsValidationExceptionWhenMatchTypeIsExactMatchAndParamsHasMultipleValues() {
        final SearchParam sut = new SearchParam();
        sut.setProperty(URI.create(SKOS.NOTATION));
        sut.setValue(Set.of("one", "two"));
        sut.setMatchType(MatchType.EXACT_MATCH);
        assertThrows(ValidationException.class, sut::validate);
    }

    @Test
    void validateThrowsValidationExceptionWhenNoValueIsProvided() {
        final SearchParam sut = new SearchParam();
        sut.setProperty(URI.create(RDF.TYPE));
        sut.setMatchType(MatchType.IRI);
        assertThrows(ValidationException.class, sut::validate);
    }
}
