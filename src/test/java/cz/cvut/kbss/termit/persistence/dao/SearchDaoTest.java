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
package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.query.Query;
import cz.cvut.kbss.jopa.model.query.TypedQuery;
import cz.cvut.kbss.termit.dto.search.MatchType;
import cz.cvut.kbss.termit.dto.search.SearchParam;
import cz.cvut.kbss.termit.dto.search.SearchResult;
import cz.cvut.kbss.termit.dto.search.SearchString;
import cz.cvut.kbss.termit.util.Constants;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchDaoTest {

    @Mock
    private EntityManager emMock;

    @Mock
    private Query ftsQueryMock;

    @Mock
    private TypedQuery<Long> ftsResultCountQueryMock;

    @Mock
    private DataDao dataDaoMock;

    private SearchDao sut;

    @BeforeEach
    void setUp() {
        this.sut = new SearchDao(emMock, dataDaoMock);
        sut.loadQueries();
    }

    private void mockSearchQuery() {
        when(emMock.createNativeQuery(anyString(), anyString())).thenReturn(ftsQueryMock);
        when(emMock.createNativeQuery(anyString(), eq(Long.class))).thenReturn(ftsResultCountQueryMock);
        when(ftsQueryMock.setParameter(anyString(), any())).thenReturn(ftsQueryMock);
        when(ftsQueryMock.setParameter(anyString(), any(), any())).thenReturn(ftsQueryMock);
        when(ftsQueryMock.getResultList()).thenReturn(Collections.emptyList());
        when(ftsResultCountQueryMock.setParameter(anyString(), any())).thenReturn(ftsResultCountQueryMock);
        when(ftsResultCountQueryMock.setParameter(anyString(), any(), any())).thenReturn(ftsResultCountQueryMock);
        when(ftsResultCountQueryMock.getSingleResult()).thenReturn(13L);
    }

    @Test
    void fullTextSearchUsesOneTokenSearchStringAsRequiredWildcardMatch() {
        mockSearchQuery();
        final String searchString = "test";
        sut.advancedSearch(new SearchString(searchString, null), Collections.emptyList(), Constants.DEFAULT_PAGE_SPEC, List.of());
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(ftsQueryMock, atLeastOnce()).setParameter(anyString(), captor.capture(), any());
        final Optional<String> argument = captor.getAllValues().stream()
                                                .filter(s -> s.equals("+" + searchString + SearchDao.LUCENE_WILDCARD))
                                                .findAny();
        assertTrue(argument.isPresent());
    }

    @Test
    void fullTextSearchUsesLastTokenInMultiTokenSearchStringAsRequiredWildcardMatch() {
        mockSearchQuery();
        final String lastToken = "token";
        final String searchString = "termOne termTwo " + lastToken;
        sut.advancedSearch(new SearchString(searchString, null), Collections.emptyList(), Constants.DEFAULT_PAGE_SPEC, List.of());
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(ftsQueryMock, atLeastOnce()).setParameter(anyString(), captor.capture(), any());
        final Optional<String> argument = captor.getAllValues().stream()
                                                .filter(s -> s.equals("+termOne +termTwo +" + lastToken + SearchDao.LUCENE_WILDCARD))
                                                .findAny();
        assertTrue(argument.isPresent());
    }

    @Test
    void fullTextSearchDoesNotAddWildcardIfLastTokenAlreadyEndsWithWildcard() {
        mockSearchQuery();
        final String searchString = "test token*";
        sut.advancedSearch(new SearchString(searchString, null), Collections.emptyList(), Constants.DEFAULT_PAGE_SPEC, List.of());
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(ftsQueryMock, atLeastOnce()).setParameter(anyString(), captor.capture(), any());
        final Optional<String> argument = captor.getAllValues().stream().filter(s -> s.startsWith(searchString))
                                                .findAny();
        assertTrue(argument.isPresent());
        assertEquals(searchString, argument.get());
    }

    @Test
    void fullTextSearchDoesNotAddWildcardForShortLastToken() {
        mockSearchQuery();
        final String searchString = "ab";
        sut.advancedSearch(new SearchString(searchString, null), Collections.emptyList(), Constants.DEFAULT_PAGE_SPEC, List.of());
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(ftsQueryMock, atLeastOnce()).setParameter(anyString(), captor.capture(), any());
        assertTrue(captor.getAllValues().stream().anyMatch(s -> s.equals("+" + searchString)));
        assertFalse(captor.getAllValues().stream().anyMatch(s -> s.equals("+" + searchString + SearchDao.LUCENE_WILDCARD)));
    }

    @Test
    void fullTextSearchReturnsEmptyResultImmediatelyWhenSearchStringIsBlank() {
        final Page<SearchResult> result = sut.advancedSearch(new SearchString("", null), Collections.emptyList(),
                                                             Constants.DEFAULT_PAGE_SPEC, List.of());
        assertTrue(result.isEmpty());
        verify(emMock, never()).createNativeQuery(any(), anyString());
    }

    @Test
    void advancedSearchWithRdfTypeFacetKeepsCanonicalTypeFilterInFtsQuery() {
        mockSearchQuery();
        final SearchParam typeParam = new SearchParam(
                URI.create(RDF.TYPE.stringValue()),
                Set.of("http://onto.fel.cvut.cz/ontologies/ufo/event"),
                MatchType.IRI
        );

        sut.advancedSearch(new SearchString("matching", null), Set.of(typeParam), Constants.DEFAULT_PAGE_SPEC, List.of());

        final ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(emMock).createNativeQuery(queryCaptor.capture(), anyString());
        final String query = queryCaptor.getValue();
        assertThat(query, containsString("?entity a ?term"));
        assertThat(query, containsString("?entity a ?vocabulary"));
        assertThat(query, containsString("?entity <" + RDF.TYPE.stringValue() + "> ?v0"));
        assertThat(query, not(containsString("FILTER (?type IN (")));
    }

    @Test
    void advancedSearchWithFullTextResolvesTotalNumberOfResults() {
        mockSearchQuery();
        final Page<SearchResult> result = sut.advancedSearch(new SearchString("matching", null), Collections.emptyList(),
                                                             Constants.DEFAULT_PAGE_SPEC, List.of());
        assertEquals(13, result.getTotalElements());
    }
}
