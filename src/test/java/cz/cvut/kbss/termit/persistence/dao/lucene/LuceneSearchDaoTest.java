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
package cz.cvut.kbss.termit.persistence.dao.lucene;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.query.Query;
import cz.cvut.kbss.termit.dto.search.FullTextSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LuceneSearchDaoTest {

    @Mock
    private EntityManager emMock;

    @Mock
    private Query queryMock;

    private LuceneSearchDao sut;

    @BeforeEach
    void setUp() {
        this.sut = new LuceneSearchDao(emMock);
        sut.loadQueries();
    }

    private void mockSearchQuery() {
        when(emMock.createNativeQuery(any(), anyString())).thenReturn(queryMock);
        when(queryMock.setParameter(anyString(), any())).thenReturn(queryMock);
        when(queryMock.setParameter(anyString(), any(), any())).thenReturn(queryMock);
        when(queryMock.getResultList()).thenReturn(Collections.emptyList());
    }

    @Test
    void fullTextSearchUsesOneTokenSearchStringAsDisjunctionOfExactAndWildcardMatch() {
        mockSearchQuery();
        final String searchString = "test";
        sut.fullTextSearch(searchString, null);
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(queryMock, atLeastOnce()).setParameter(anyString(), captor.capture(), any());
        final Optional<String> argument = captor.getAllValues().stream().filter(s -> s
                .equals(searchString + " " + searchString + LuceneSearchDao.LUCENE_WILDCARD))
                                                .findAny();
        assertTrue(argument.isPresent());
    }

    @Test
    void fullTextSearchUsesLastTokenInMultiTokenSearchStringAsDisjunctionOfExactAndWildcardMatch() {
        mockSearchQuery();
        final String lastToken = "token";
        final String searchString = "termOne termTwo " + lastToken;
        sut.fullTextSearch(searchString, null);
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(queryMock, atLeastOnce()).setParameter(anyString(), captor.capture(), any());
        final Optional<String> argument = captor.getAllValues().stream()
                                                .filter(s -> s.equals(searchString + " " + lastToken + LuceneSearchDao.LUCENE_WILDCARD))
                                                .findAny();
        assertTrue(argument.isPresent());
    }

    @Test
    void fullTextSearchDoesNotAddWildcardIfLastTokenAlreadyEndsWithWildcard() {
        mockSearchQuery();
        final String searchString = "test token*";
        sut.fullTextSearch(searchString, null);
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(queryMock, atLeastOnce()).setParameter(anyString(), captor.capture(), any());
        final Optional<String> argument = captor.getAllValues().stream().filter(s -> s.startsWith(searchString))
                                                .findAny();
        assertTrue(argument.isPresent());
        assertEquals(searchString, argument.get());
    }

    @Test
    void fullTextSearchReturnsEmptyResultImmediatelyWhenSearchStringIsBlank() {
        final List<FullTextSearchResult> result = sut.fullTextSearch("", null);
        assertTrue(result.isEmpty());
        verify(emMock, never()).createNativeQuery(anyString());
    }
}
