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
package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.vocabulary.RDF;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.search.FacetedSearchResult;
import cz.cvut.kbss.termit.dto.search.FullTextSearchResult;
import cz.cvut.kbss.termit.dto.search.MatchType;
import cz.cvut.kbss.termit.dto.search.SearchParam;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.ValidationException;
import cz.cvut.kbss.termit.persistence.dao.SearchDao;
import cz.cvut.kbss.termit.util.Constants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private SearchDao searchDao;

    @InjectMocks
    private SearchService sut;

    @Test
    void fullTextSearchFiltersResultsFromNonMatchingVocabularies() {
        final String searchString = "test";
        final URI vocabulary = Generator.generateUri();
        final FullTextSearchResult ftsr = new FullTextSearchResult(
                Generator.generateUri(),
                "test",
                "Term definition",
                vocabulary,
                null,
                SKOS.CONCEPT,
                "test",
                "test",
                1.0);
        when(searchDao.fullTextSearchIncludingSnapshots(searchString)).thenReturn(Collections.singletonList(ftsr));
        final List<FullTextSearchResult> result = sut.fullTextSearchOfTerms(searchString, Collections.singleton(
                Generator.generateUri()));
        assertTrue(result.isEmpty());
        verify(searchDao).fullTextSearchIncludingSnapshots(searchString);
    }

    @Test
    void fullTextSearchReturnsResultsFromMatchingVocabularies() {
        final String searchString = "test";
        final URI vocabulary = Generator.generateUri();
        final FullTextSearchResult ftsr = new FullTextSearchResult(
                Generator.generateUri(),
                "test",
                "Term definition",
                vocabulary,
                Generator.generateUri(),
                SKOS.CONCEPT,
                "test",
                "test",
                1.0);
        when(searchDao.fullTextSearchIncludingSnapshots(searchString)).thenReturn(Collections.singletonList(ftsr));
        final List<FullTextSearchResult> result = sut.fullTextSearchOfTerms(searchString,
                                                                            Collections.singleton(vocabulary));
        assertEquals(Collections.singletonList(ftsr), result);
        verify(searchDao).fullTextSearchIncludingSnapshots(searchString);
    }

    @Test
    void facetedTermSearchValidatesEachSearchParamBeforeInvokingSearch() {
        final SearchParam spOne = new SearchParam(URI.create(RDF.TYPE), Set.of(Generator.generateUriString()), MatchType.IRI);
        final SearchParam spTwo = new SearchParam(URI.create(SKOS.NOTATION), Set.of("will be removed"), MatchType.SUBSTRING);
        spTwo.setValue(null);
        assertThrows(ValidationException.class, () -> sut.facetedTermSearch(List.of(spOne, spTwo), Constants.DEFAULT_PAGE_SPEC));
        verify(searchDao, never()).facetedTermSearch(anyCollection(), any());
    }

    @Test
    void facetedTermSearchExecutesSearchOnDaoAndReturnsResults() {
        final SearchParam spOne = new SearchParam(URI.create(RDF.TYPE), Set.of(Generator.generateUriString()), MatchType.IRI);
        final FacetedSearchResult item = new FacetedSearchResult();
        item.setUri(Generator.generateUri());
        item.setLabel(MultilingualString.create("Test term", Environment.LANGUAGE));
        item.setVocabulary(Generator.generateUri());
        item.setTypes(new HashSet<>(spOne.getValue()));
        when(searchDao.facetedTermSearch(anyCollection(), any(Pageable.class))).thenReturn(List.of(item));
        final Pageable pageSpec = PageRequest.of(2, 100);

        final List<FacetedSearchResult> result = sut.facetedTermSearch(Set.of(spOne), pageSpec);
        assertEquals(List.of(item), result);
        verify(searchDao).facetedTermSearch(Set.of(spOne), pageSpec);
    }
}
