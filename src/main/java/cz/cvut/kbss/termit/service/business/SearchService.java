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
import cz.cvut.kbss.termit.dto.search.FullTextSearchResult;
import cz.cvut.kbss.termit.dto.search.MatchType;
import cz.cvut.kbss.termit.dto.search.SearchParam;
import cz.cvut.kbss.termit.persistence.dao.SearchDao;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SearchService {

    private final SearchDao searchDao;

    @Autowired
    public SearchService(SearchDao searchDao) {
        this.searchDao = searchDao;
    }


    /**
     * Executes full text search in terms, possibly filtered by vocabularies.
     * <p>
     * This method now uses the advanced search internally with type and vocabulary filters.
     *
     * @param searchString String to search by
     * @param vocabularies URIs of vocabularies to search in, or empty set to search all vocabularies
     * @param language The language of the {@code searchString}, {@code null} to match all languages
     * @return Matching terms
     */
    @PostFilter("@searchAuthorizationService.canRead(filterObject)")
    public List<FullTextSearchResult> fullTextSearchOfTerms(String searchString, Set<URI> vocabularies, String language) {
        Objects.requireNonNull(vocabularies);

        Collection<SearchParam> searchParams = new ArrayList<>();
        searchParams.add(new SearchParam(
            URI.create(RDF.TYPE),
            Set.of(SKOS.CONCEPT),
            MatchType.IRI
        ));
        if (!vocabularies.isEmpty()) {
            searchParams.add(new SearchParam(
                URI.create(Vocabulary.s_p_je_pojmem_ze_slovniku),
                vocabularies.stream().map(URI::toString).collect(Collectors.toSet()),
                MatchType.IRI
            ));
        }

        // advancedSearch with unpaged results to maintain backwards compatibility
        return advancedSearch(searchString, language, searchParams, Pageable.unpaged());
    }

    /**
     * Executes advanced search combining full text search with faceted filtering.
     *
     * @param searchString String to search by for full text search
     * @param language     The language of the {@code searchString}, {@code null} to match all languages
     * @param searchParams Search parameters for filtering by facets.
     * @param pageSpec     Specification of the page of results to return
     * @return Matching results
     */
    @PostFilter("@searchAuthorizationService.canRead(filterObject)")
    public List<FullTextSearchResult> advancedSearch(String searchString, String language,
                                                     Collection<SearchParam> searchParams,
                                                     Pageable pageSpec) {
        Objects.requireNonNull(searchParams);
        searchParams.forEach(SearchParam::validate);
        return searchDao.advancedSearch(searchString, language, searchParams, pageSpec);
    }
}
