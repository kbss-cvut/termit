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
package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.query.Query;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.search.FacetedSearchResult;
import cz.cvut.kbss.termit.dto.search.FullTextSearchResult;
import cz.cvut.kbss.termit.dto.search.SearchParam;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Utils;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Repository
@Profile("!lucene")
public class SearchDao {

    private static final String FTS_QUERY_FILE = "fulltextsearch.rq";

    private static final Logger LOG = LoggerFactory.getLogger(SearchDao.class);

    private final Configuration.Persistence config;

    protected String ftsQuery;

    protected final EntityManager em;

    @Autowired
    public SearchDao(EntityManager em, Configuration config) {
        this.em = em;
        this.config = config.getPersistence();
    }

    @PostConstruct
    private void loadQueries() {
        this.ftsQuery = Utils.loadQuery(FTS_QUERY_FILE);
    }

    /**
     * Finds terms and vocabularies that match the specified search string.
     * <p>
     * The search functionality depends on the underlying repository and the index it uses. But basically the search
     * looks for match in asset label, comment and SKOS definition (if exists).
     * <p>
     * Note that this version of the search excludes asset snapshots from the results.
     *
     * @param searchString The string to search by
     * @return List of matching results
     * @see #fullTextSearchIncludingSnapshots(String)
     */
    public List<FullTextSearchResult> fullTextSearch(@NonNull String searchString) {
        Objects.requireNonNull(searchString);
        if (searchString.isBlank()) {
            return Collections.emptyList();
        }
        LOG.trace("Running full text search for search string \"{}\".", searchString);
        return setCommonQueryParams(em.createNativeQuery(ftsQuery, "FullTextSearchResult"), searchString)
                .setParameter("snapshot", URI.create(Vocabulary.s_c_verze_objektu))
                .getResultList();
    }

    /**
     * Finds terms and vocabularies that match the specified search string.
     * <p>
     * The search functionality depends on the underlying repository and the index it uses. But basically the search
     * looks for match in asset label, comment and SKOS definition (if exists).
     * <p>
     * Note that this version of the search includes asset snapshots.
     *
     * @param searchString The string to search by
     * @return List of matching results
     * @see #fullTextSearchIncludingSnapshots(String)
     */
    public List<FullTextSearchResult> fullTextSearchIncludingSnapshots(@NonNull String searchString) {
        Objects.requireNonNull(searchString);
        if (searchString.isBlank()) {
            return Collections.emptyList();
        }
        LOG.trace("Running full text search (including snapshots) for search string \"{}\".", searchString);
        return setCommonQueryParams(em.createNativeQuery(queryIncludingSnapshots(), "FullTextSearchResult"),
                                    searchString).getResultList();
    }

    protected Query setCommonQueryParams(Query q, String searchString) {
        return q.setParameter("term", URI.create(SKOS.CONCEPT))
                .setParameter("vocabulary", URI.create(Vocabulary.s_c_slovnik))
                .setParameter("inVocabulary",
                        URI.create(Vocabulary.s_p_je_pojmem_ze_slovniku))
                .setParameter("hasState", URI.create(Vocabulary.s_p_ma_stav_pojmu))
                .setParameter("langTag", config.getLanguage(), null)
                .setParameter("searchString", searchString, null);
    }

    protected String queryIncludingSnapshots() {
        // This string has to match the filter string in the query
        return ftsQuery.replace("FILTER NOT EXISTS { ?entity a ?snapshot . }", "");
    }

    /**
     * Executes a faceted search among terms using the specified search parameters.
     * <p>
     * Only current versions of terms are searched.
     *
     * @param searchParams Search parameters (facets)
     * @param pageSpec     Specification of the page of results to return
     * @return List of matching terms, ordered by label
     */
    public List<FacetedSearchResult> facetedTermSearch(@NonNull Collection<SearchParam> searchParams,
                                                       @NonNull Pageable pageSpec) {
        Objects.requireNonNull(searchParams);
        Objects.requireNonNull(pageSpec);
        LOG.trace("Running faceted term search for search parameters: {}", searchParams);
        final StringBuilder queryStr = new StringBuilder(
                "SELECT DISTINCT ?t WHERE { ?t a ?term ; ?hasLabel ?label .\n");
        int i = 0;
        for (SearchParam p : searchParams) {
            final String variable = "?v" + i++;
            queryStr.append("?t ").append(Utils.uriToString(p.getProperty())).append(" ").append(variable)
                    .append(" . ");
            switch (p.getMatchType()) {
                case IRI:
                    queryStr.append("FILTER (").append(variable).append(" IN (")
                            .append(p.getValue().stream().map(v -> Utils.uriToString(URI.create(v))).collect(
                                    Collectors.joining(","))).append("))\n");
                    break;
                case EXACT_MATCH:
                    queryStr.append("FILTER (STR(").append(variable).append(") = \"")
                            .append(p.getValue().iterator().next()).append("\")\n");
                    break;
                case SUBSTRING:
                    queryStr.append("FILTER (CONTAINS(LCASE(STR(").append(variable).append(")), LCASE(\"")
                            .append(p.getValue().iterator().next()).append("\")))\n");
                    break;
            }
        }
        queryStr.append("FILTER NOT EXISTS { ?t a ?snapshot . }} ORDER BY ?label");
        return em.createNativeQuery(queryStr.toString(), FacetedSearchResult.class)
                 .setParameter("term", URI.create(SKOS.CONCEPT))
                 .setParameter("hasLabel", URI.create(SKOS.PREF_LABEL))
                 .setParameter("snapshot", URI.create(Vocabulary.s_c_verze_objektu))
                 .setFirstResult((int) pageSpec.getOffset())
                 .setMaxResults(pageSpec.getPageSize())
                 .getResultList();
    }
}
