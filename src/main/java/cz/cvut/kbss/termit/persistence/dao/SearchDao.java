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
import cz.cvut.kbss.termit.dto.FullTextSearchResult;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Utils;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

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
    public List<FullTextSearchResult> fullTextSearch(String searchString) {
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
    public List<FullTextSearchResult> fullTextSearchIncludingSnapshots(String searchString) {
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
                .setParameter("isDraft", URI.create(Vocabulary.s_p_je_draft))
                .setParameter("langTag", config.getLanguage(), null)
                .setParameter("searchString", searchString, null);
    }

    protected String queryIncludingSnapshots() {
        // This string has to match the filter string in the query
        return ftsQuery.replace("FILTER NOT EXISTS { ?entity a ?snapshot . }", "");
    }
}
