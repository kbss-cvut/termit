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
package cz.cvut.kbss.termit.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.vocabulary.RDF;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.search.FacetedSearchResult;
import cz.cvut.kbss.termit.dto.search.FullTextSearchResult;
import cz.cvut.kbss.termit.dto.search.MatchType;
import cz.cvut.kbss.termit.dto.search.SearchParam;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.service.business.SearchService;
import cz.cvut.kbss.termit.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SearchControllerTest extends BaseControllerTestRunner {

    private static final String PATH = "/search";

    @Mock
    private SearchService searchServiceMock;

    @InjectMocks
    private SearchController sut;

    @BeforeEach
    void setUp() {
        super.setUp(sut);
    }

    @Test
    void fullTextSearchExecutesSearchOnService() throws Exception {
        final List<FullTextSearchResult> expected = Collections
                .singletonList(
                        new FullTextSearchResult(Generator.generateUri(), "test", null, null, null, SKOS.CONCEPT,
                                                 "test", "test", 1.0));
        when(searchServiceMock.fullTextSearch(any())).thenReturn(expected);
        final String searchString = "test";

        final MvcResult mvcResult = mockMvc.perform(get(PATH + "/fts").param("searchString", searchString))
                                           .andExpect(status().isOk()).andReturn();
        final List<FullTextSearchResult> result = readValue(mvcResult, new TypeReference<List<FullTextSearchResult>>() {
        });
        assertEquals(expected.size(), result.size());
        assertEquals(expected.get(0).getUri(), result.get(0).getUri());
        assertEquals(expected.get(0).getLabel(), result.get(0).getLabel());
        assertEquals(expected.get(0).getTypes(), result.get(0).getTypes());
        verify(searchServiceMock).fullTextSearch(searchString);
    }

    @Test
    void fullTextSearchOfTermsWithoutVocabularySpecificationExecutesSearchOnService() throws Exception {
        final URI vocabularyIri = URI.create("https://test.org/vocabulary");
        final List<FullTextSearchResult> expected = Collections
                .singletonList(new FullTextSearchResult(Generator.generateUri(), "test", "Term definition", vocabularyIri, null,
                                                        SKOS.CONCEPT, "test", "test", 1.0));
        when(searchServiceMock.fullTextSearchOfTerms(any(), any())).thenReturn(expected);
        final String searchString = "test";

        mockMvc.perform(get(PATH + "/fts/terms")
                                .param("searchString", searchString)
                                .param("vocabulary", vocabularyIri.toString()))
               .andExpect(status().isOk()).andReturn();
        verify(searchServiceMock).fullTextSearchOfTerms(searchString, Collections.singleton(vocabularyIri));
    }

    @Test
    void facetedTermSearchPassesSearchParametersToSearchService() throws Exception {
        final FacetedSearchResult term = new FacetedSearchResult();
        term.setUri(Generator.generateUri());
        term.setLabel(MultilingualString.create("Test term", Environment.LANGUAGE));
        when(searchServiceMock.facetedTermSearch(anyCollection(), any(Pageable.class))).thenReturn(List.of(term));
        final List<SearchParam> searchParams = List.of(
                new SearchParam(URI.create(SKOS.NOTATION), Set.of("LA_"), MatchType.EXACT_MATCH),
                new SearchParam(URI.create(RDF.TYPE), Set.of(Generator.generateUri().toString()), MatchType.IRI));

        final MvcResult mvcResult = mockMvc.perform(
                post(PATH + "/faceted/terms").content(toJson(searchParams)).contentType(
                        MediaType.APPLICATION_JSON)).andReturn();
        final List<FacetedSearchResult> result = readValue(mvcResult, new TypeReference<List<FacetedSearchResult>>() {
        });
        assertEquals(List.of(term), result);
        verify(searchServiceMock).facetedTermSearch(searchParams, Constants.DEFAULT_PAGE_SPEC);
    }

    @Test
    void facetedSearchPassesSpecifiedPageSpecificationToService() throws Exception {
        final FacetedSearchResult term = new FacetedSearchResult();
        term.setUri(Generator.generateUri());
        term.setLabel(MultilingualString.create("Test term", Environment.LANGUAGE));
        when(searchServiceMock.facetedTermSearch(anyCollection(), any(Pageable.class))).thenReturn(List.of(term));
        final List<SearchParam> searchParams = List.of(
                new SearchParam(URI.create(SKOS.NOTATION), Set.of("LA_"), MatchType.EXACT_MATCH));
        final int pageNo = Generator.randomInt(0, 5);
        final int pageSize = Generator.randomInt(100, 1000);

        mockMvc.perform(
                post(PATH + "/faceted/terms").content(toJson(searchParams)).contentType(
                        MediaType.APPLICATION_JSON).param(Constants.QueryParams.PAGE, Integer.toString(pageNo)).param(
                        Constants.QueryParams.PAGE_SIZE, Integer.toString(pageSize))).andExpect(status().isOk());
        verify(searchServiceMock).facetedTermSearch(searchParams, PageRequest.of(pageNo, pageSize));
    }

    @Test
    void facetedSearchThrowsBadRequestWhenNoSearchParamsAreProvided() throws Exception {
        mockMvc.perform(post(PATH + "/faceted/terms").content("[]").contentType(MediaType.APPLICATION_JSON))
               .andExpect(status().isBadRequest());
        verify(searchServiceMock, never()).facetedTermSearch(anyCollection(), any());
    }
}
