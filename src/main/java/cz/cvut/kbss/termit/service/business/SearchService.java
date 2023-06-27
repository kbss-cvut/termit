/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If not, see
 * <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.search.FacetedSearchResult;
import cz.cvut.kbss.termit.dto.search.FullTextSearchResult;
import cz.cvut.kbss.termit.dto.search.SearchParam;
import cz.cvut.kbss.termit.persistence.dao.SearchDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.stereotype.Service;

import java.net.URI;
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
     * Executes full text search in assets.
     *
     * @param searchString String to search by
     * @return Matching assets
     */
    @PostFilter("@searchAuthorizationService.canRead(filterObject)")
    public List<FullTextSearchResult> fullTextSearch(String searchString) {
        return searchDao.fullTextSearch(searchString);
    }

    /**
     * Executes full text search in terms, possibly filtered by vocabularies.
     *
     * @param searchString String to search by
     * @param vocabularies URIs of vocabularies to search in, or null, if all vocabularies shall be searched
     * @return Matching terms
     */
    @PostFilter("@searchAuthorizationService.canRead(filterObject)")
    public List<FullTextSearchResult> fullTextSearchOfTerms(String searchString, Set<URI> vocabularies) {
        Objects.requireNonNull(vocabularies);
        // Search including snapshots, as the selected vocabularies may be snapshots
        return searchDao.fullTextSearchIncludingSnapshots(searchString).stream()
                        .filter(r -> r.getTypes().contains(SKOS.CONCEPT))
                        .filter(r -> vocabularies.contains(r.getVocabulary()))
                        .collect(Collectors.toList());
    }

    /**
     * Executes a faceted search of terms based on the specified search parameters.
     * <p>
     * The search parameters define facets by which terms should be searched together with corresponding search values.
     * The search treats the parameters as conjunction, so the result has to match all the search parameters.
     * <p>
     * Note: cannot use full paging support, as it is not supported by Spring Security -
     * <a href="https://github.com/spring-projects/spring-security/issues/3410">https://github.com/spring-projects/spring-security/issues/3410</a>
     *
     * @param searchParams Search parameters
     * @param pageSpec     Page specifying result number and position
     * @return List of matching terms, sorted by label
     */
    @PostFilter("@searchAuthorizationService.canRead(filterObject)")
    public List<FacetedSearchResult> facetedTermSearch(@NonNull Collection<SearchParam> searchParams,
                                                       @NonNull Pageable pageSpec) {
        Objects.requireNonNull(searchParams);
        Objects.requireNonNull(pageSpec);
        searchParams.forEach(SearchParam::validate);
        return searchDao.facetedTermSearch(searchParams, pageSpec);
    }
}
