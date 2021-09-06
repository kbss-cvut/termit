/**
 * TermIt
 * Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.FullTextSearchResult;
import cz.cvut.kbss.termit.persistence.dao.SearchDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
     * @param vocabularies URIs of vocabularies to search in, or null, if all vocabularies shall be searched
     * @param termsOnly whether to return only matches related to terms (not vocabularies)
     * @return Matching assets
     */
    public List<FullTextSearchResult> fullTextSearch(String searchString, Set<URI> vocabularies, boolean termsOnly) {
        Stream<FullTextSearchResult> result = searchDao.fullTextSearch(searchString).stream();
        if (termsOnly) {
            result = result.filter(r -> r.getTypes().contains(SKOS.CONCEPT));
        }
        if (vocabularies != null) {
            result = result.filter(r -> vocabularies.contains(r.getVocabulary()));
        }
        return result.collect(Collectors.toList());
    }
}
