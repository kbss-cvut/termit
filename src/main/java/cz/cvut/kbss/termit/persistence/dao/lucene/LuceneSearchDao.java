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
package cz.cvut.kbss.termit.persistence.dao.lucene;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.dto.FullTextSearchResult;
import cz.cvut.kbss.termit.persistence.dao.SearchDao;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * {@link SearchDao} extension for Lucene-based repositories. These support rich search strings with wildcards and
 * operators.
 * <p>
 * This DAO automatically adds a wildcard to the last token in the search string, so that results for incomplete words
 * are returned as well.
 */
@Repository
@Profile("lucene")  // Corresponds to a profile set in pom.xml
public class LuceneSearchDao extends SearchDao {

    private static final Logger LOG = LoggerFactory.getLogger(LuceneSearchDao.class);

    static final char LUCENE_WILDCARD = '*';

    public LuceneSearchDao(EntityManager em, Configuration config) {
        super(em, config);
    }

    @Override
    public List<FullTextSearchResult> fullTextSearch(String searchString) {
        Objects.requireNonNull(searchString);
        if (searchString.isBlank()) {
            return Collections.emptyList();
        }
        final String wildcardString = addWildcard(searchString);
        final String exactMatch = splitExactMatch(searchString);
        LOG.trace("Running full text search for search string \"{}\", using wildcard variant \"{}\".", searchString,
                  wildcardString);
        return (List<FullTextSearchResult>) setCommonQueryParams(em.createNativeQuery(ftsQuery, "FullTextSearchResult"),
                                                                 searchString)
                .setParameter("snapshot", URI.create(Vocabulary.s_c_verze_objektu))
                .setParameter("wildCardSearchString", wildcardString, null)
                .setParameter("splitExactMatch", exactMatch, null)
                .getResultList();
    }

    private static String addWildcard(String searchString) {
        // Search string already contains a wildcard
        if (searchString.charAt(searchString.length() - 1) == LUCENE_WILDCARD) {
            return searchString;
        }
        // Append the last token also with a wildcard
        final String[] split = searchString.split("\\s+");
        final String lastTokenWithWildcard = split[split.length - 1] + LUCENE_WILDCARD;
        return String.join(" ", split) + " " + lastTokenWithWildcard;
    }

    private static String splitExactMatch(String searchString) {
        final String[] split = searchString.trim().split("\\s+");
        String s = "<em>";
        return s.concat(String.join("</em> <em>", split)).concat("</em>");
    }

    @Override
    public List<FullTextSearchResult> fullTextSearchIncludingSnapshots(String searchString) {
        Objects.requireNonNull(searchString);
        if (searchString.isBlank()) {
            return Collections.emptyList();
        }
        final String wildcardString = addWildcard(searchString);
        final String exactMatch = splitExactMatch(searchString);
        LOG.trace(
                "Running full text search (including snapshots) for search string \"{}\", using wildcard variant \"{}\".",
                searchString, wildcardString);
        return (List<FullTextSearchResult>) setCommonQueryParams(em.createNativeQuery(queryIncludingSnapshots(), "FullTextSearchResult"), searchString)
                .setParameter("wildCardSearchString", wildcardString, null)
                .setParameter("splitExactMatch", exactMatch, null)
                .getResultList();
    }
}
