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
package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.jopa.vocabulary.RDF;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.listing.VocabularyDto;
import cz.cvut.kbss.termit.dto.mapper.DtoMapper;
import cz.cvut.kbss.termit.dto.search.MatchType;
import cz.cvut.kbss.termit.dto.search.SearchParam;
import cz.cvut.kbss.termit.dto.search.SearchResult;
import cz.cvut.kbss.termit.dto.search.SearchString;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.ValidationException;
import cz.cvut.kbss.termit.model.AbstractEntity;
import cz.cvut.kbss.termit.persistence.dao.SearchDao;
import cz.cvut.kbss.termit.service.security.authorization.VocabularyAuthorizationService;
import cz.cvut.kbss.termit.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private SearchDao searchDao;

    @Mock
    private VocabularyAuthorizationService authService;

    @InjectMocks
    private SearchService sut;

    private List<VocabularyDto> allowedVocabularies;
    private List<URI> allowedVocabularyUris;

    @BeforeEach
    void setUp() {
        this.allowedVocabularies = IntStream.range(0, 5)
                                            .mapToObj(i -> Generator.generateVocabularyWithId())
                                            .map(v -> {
                                                final DtoMapper dtoMapper = Environment.getDtoMapper();
                                                return dtoMapper.vocabularyToVocabularyDto(v);
                                            }).toList();
        this.allowedVocabularyUris = allowedVocabularies.stream().map(AbstractEntity::getUri).toList();
    }

    @Test
    void advancedSearchValidatesEachSearchParamBeforeInvokingSearch() {
        final SearchParam spOne = new SearchParam(URI.create(RDF.TYPE), Set.of(Generator.generateUriString()),
                                                  MatchType.IRI);
        final SearchParam spTwo = new SearchParam(URI.create(SKOS.NOTATION), Set.of("will be removed"),
                                                  MatchType.SUBSTRING);
        spTwo.setValue(null);
        final List<SearchParam> params = List.of(spOne, spTwo);
        assertThrows(ValidationException.class,
                     () -> sut.advancedSearch(new SearchString("test", null), params, Constants.DEFAULT_PAGE_SPEC));
        verify(searchDao, never()).advancedSearch(any(), anyCollection(), any(), eq(allowedVocabularyUris));
    }

    @Test
    void advancedSearchExecutesSearchOnDaoAndReturnsResults() {
        when(authService.getReadableVocabularies()).thenReturn(allowedVocabularies);
        final SearchParam spOne = new SearchParam(URI.create(RDF.TYPE), Set.of(Generator.generateUriString()),
                                                  MatchType.IRI);
        final SearchResult item = new SearchResult(
                Generator.generateUri(), "Test term", null, Generator.generateUri(), null,
                SKOS.CONCEPT, "test", "test", 1.0);
        when(searchDao.advancedSearch(any(), anyCollection(), any(Pageable.class), anyCollection())).thenReturn(
                new PageImpl<>(List.of(item)));
        final Pageable pageSpec = PageRequest.of(2, 100);

        final SearchString searchString = new SearchString("test", null);
        final Page<SearchResult> result = sut.advancedSearch(searchString, Set.of(spOne), pageSpec);
        assertEquals(List.of(item), result.getContent());
        verify(searchDao).advancedSearch(eq(searchString), eq(Set.of(spOne)), eq(pageSpec), eq(allowedVocabularyUris));
    }

    @Test
    void fullTextSearchOfTermsAddsConceptAndVocabularyFilters() {
        when(authService.getReadableVocabularies()).thenReturn(allowedVocabularies);
        final SearchString searchString = new SearchString("test", null);
        final URI vocabulary = Generator.generateUri();
        final SearchResult item = new SearchResult(
                Generator.generateUri(),
                "test",
                "Term definition",
                vocabulary,
                null,
                SKOS.CONCEPT,
                "test",
                "test",
                1.0);
        when(searchDao.advancedSearch(any(), anyCollection(), any(Pageable.class), anyCollection())).thenReturn(
                new PageImpl<>(List.of(item)));

        final List<SearchResult> result = sut.fullTextSearchOfTerms(searchString, Set.of(vocabulary));

        assertEquals(List.of(item), result);
        verify(searchDao).advancedSearch(eq(searchString), argThat(params ->
                                                                           params.size() == 2
                                                                                   && params.stream().anyMatch(
                                                                                   p -> p.getProperty().toString()
                                                                                         .equals(RDF.TYPE)
                                                                                           && p.getMatchType() == MatchType.IRI
                                                                                           && p.getValue()
                                                                                               .equals(Set.of(
                                                                                                       SKOS.CONCEPT)))
                                                                                   && params.stream().anyMatch(
                                                                                   p -> p.getProperty().toString()
                                                                                         .equals(SKOS.IN_SCHEME)
                                                                                           && p.getMatchType() == MatchType.IRI
                                                                                           && p.getValue()
                                                                                               .equals(Set.of(
                                                                                                       vocabulary.toString())))),
                                         argThat(pageable -> !pageable.isPaged()), eq(allowedVocabularyUris));
    }

    @Test
    void fullTextSearchOfTermsAddsOnlyConceptFilterWhenVocabularyNotSpecified() {
        when(authService.getReadableVocabularies()).thenReturn(allowedVocabularies);
        final SearchString searchString = new SearchString("test", null);
        final SearchResult item = new SearchResult(
                Generator.generateUri(),
                "test",
                "Term definition",
                Generator.generateUri(),
                null,
                SKOS.CONCEPT,
                "test",
                "test",
                1.0);
        when(searchDao.advancedSearch(any(), anyCollection(), any(Pageable.class), anyCollection())).thenReturn(
                new PageImpl<>(List.of(item)));

        final List<SearchResult> result = sut.fullTextSearchOfTerms(searchString, Collections.emptySet());

        assertEquals(List.of(item), result);
        verify(searchDao).advancedSearch(eq(searchString), argThat(params ->
                                                                           params.size() == 1
                                                                                   && params.stream().anyMatch(
                                                                                   p -> p.getProperty().toString()
                                                                                         .equals(RDF.TYPE)
                                                                                           && p.getMatchType() == MatchType.IRI
                                                                                           && p.getValue()
                                                                                               .equals(Set.of(
                                                                                                       SKOS.CONCEPT)))),
                                         argThat(pageable -> !pageable.isPaged()), eq(allowedVocabularyUris));
    }
}
