/**
 * TermIt
 * Copyright (C) 2019 Czech Technical University in Prague
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

import cz.cvut.kbss.jsonld.JsonLd;
import cz.cvut.kbss.termit.dto.FullTextSearchResult;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.SearchService;
import cz.cvut.kbss.termit.util.Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/search")
public class SearchController extends BaseController {

    private final SearchService searchService;

    @Autowired
    public SearchController(IdentifierResolver idResolver, Configuration config, SearchService searchService) {
        super(idResolver, config);
        this.searchService = searchService;
    }

    @PreAuthorize("permitAll()")
    @RequestMapping(value = "/fts", method = RequestMethod.GET, produces = {MediaType.APPLICATION_JSON_VALUE,
            JsonLd.MEDIA_TYPE})
    public List<FullTextSearchResult> fullTextSearch(@RequestParam(name = "searchString") String searchString) {
        return searchService.fullTextSearch(searchString);
    }

    @PreAuthorize("permitAll()")
    @RequestMapping(value = "/fts/terms", method = RequestMethod.GET, produces = {MediaType.APPLICATION_JSON_VALUE,
                                                                            JsonLd.MEDIA_TYPE})
    public List<FullTextSearchResult> fullTextSearchTerms(
            @RequestParam(name = "searchString") String searchString,
            @RequestParam(name = "vocabulary", required = false) Set<URI> vocabularies
    ) {
        return searchService.fullTextSearchOfTerms(searchString, vocabularies);
    }
}
