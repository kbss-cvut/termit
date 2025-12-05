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
package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.query.Query;
import cz.cvut.kbss.jopa.vocabulary.RDF;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.search.FacetedSearchResult;
import cz.cvut.kbss.termit.dto.search.FullTextSearchResult;
import cz.cvut.kbss.termit.dto.search.MatchType;
import cz.cvut.kbss.termit.dto.search.SearchParam;
import cz.cvut.kbss.termit.model.CustomAttribute;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.Utils;
import cz.cvut.kbss.termit.util.Vocabulary;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Search data access object using Lucene-based repositories. These support rich search strings with wildcards and
 * operators.
 * <p>
 * This DAO automatically adds a wildcard to the last token in the search string, so that results for incomplete words
 * are returned as well.
 */
@Repository
public class SearchDao {
    private static final String FTS_QUERY_FILE = "fulltextsearch.rq";

    private static final Logger LOG = LoggerFactory.getLogger(SearchDao.class);

    static final char LUCENE_WILDCARD = '*';

    private final EntityManager em;
    private final DataDao dataDao;
    protected String ftsQuery;

    public SearchDao(EntityManager em, DataDao dataDao) {
        this.em = em;
        this.dataDao = dataDao;
    }

    @PostConstruct
    void loadQueries() {
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
     * @param language     The language of the {@code searchString}, {@code null} to match all languages
     * @return List of matching results
     * @see #fullTextSearchIncludingSnapshots(String, String)
     */
    public List<FullTextSearchResult> fullTextSearch(@Nonnull String searchString, @Nullable String language) {
        Objects.requireNonNull(searchString);
        if (searchString.isBlank()) {
            return Collections.emptyList();
        }

        String query = adjustQueryForLanguage(ftsQuery, language);

        final String wildcardString = addWildcard(searchString);
        final String exactMatch = splitExactMatch(searchString);
        LOG.trace("Running full text search for search string \"{}\", using wildcard variant \"{}\".", searchString,
                  wildcardString);
        return setCommonQueryParams(em.createNativeQuery(query, "FullTextSearchResult"),
                                    searchString, language)
                .setParameter("snapshot", URI.create(Vocabulary.s_c_verze_objektu))
                .setParameter("wildCardSearchString", wildcardString, null)
                .setParameter("splitExactMatch", exactMatch, null)
                .getResultList();
    }

    private static String adjustQueryForLanguage(String query, String language) {
        if (language == null) {
            // BIND using unbound expression needs to be removed from the FTS query
            return query.replace("BIND (?requestedLanguageVal AS ?requestedLanguage)", "");
        }
        return query;
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

    /**
     * Finds terms and vocabularies that match the specified search string.
     * <p>
     * The search functionality depends on the underlying repository and the index it uses. But basically the search
     * looks for match in asset label, comment and SKOS definition (if exists).
     * <p>
     * Note that this version of the search includes asset snapshots.
     *
     * @param searchString The string to search by
     * @param language     The language of the {@code searchString}, {@code null} to match all languages
     * @return List of matching results
     * @see #fullTextSearchIncludingSnapshots(String, String)
     */
    public List<FullTextSearchResult> fullTextSearchIncludingSnapshots(@Nonnull String searchString,
                                                                       @Nullable String language) {
        Objects.requireNonNull(searchString);
        if (searchString.isBlank()) {
            return Collections.emptyList();
        }
        String query = adjustQueryForLanguage(queryIncludingSnapshots(), language);

        final String wildcardString = addWildcard(searchString);
        final String exactMatch = splitExactMatch(searchString);
        LOG.trace(
                "Running full text search (including snapshots) for search string \"{}\", using wildcard variant \"{}\".",
                searchString, wildcardString);
        return setCommonQueryParams(em.createNativeQuery(query, "FullTextSearchResult"),
                                    searchString, language)
                .setParameter("wildCardSearchString", wildcardString, null)
                .setParameter("splitExactMatch", exactMatch, null)
                .getResultList();
    }

    protected String queryIncludingSnapshots() {
        // This string has to match the filter string in the query
        return ftsQuery.replace("FILTER NOT EXISTS { ?entity a ?snapshot . }", "");
    }

    protected Query setCommonQueryParams(Query q, String searchString, String requestedLanguage) {
        String langSuffix = requestedLanguage == null ? "" : requestedLanguage;
        URI labelIndex = URI.create(Constants.LUCENE_CONNECTOR_LABEL_INDEX_PREFIX + langSuffix);
        URI defcomIndex = URI.create(Constants.LUCENE_CONNECTOR_DEFCOM_INDEX_PREFIX + langSuffix);
        q.setParameter("label_index", labelIndex).setParameter("defcom_index", defcomIndex);

        q.setParameter("term", URI.create(SKOS.CONCEPT))
         .setParameter("vocabulary", URI.create(Vocabulary.s_c_slovnik))
         .setParameter("inVocabulary", URI.create(Vocabulary.s_p_je_pojmem_ze_slovniku))
         .setParameter("hasState", URI.create(Vocabulary.s_p_ma_stav_pojmu))
         .setParameter("searchString", searchString, null);
        if (requestedLanguage != null) {
            q.setParameter("requestedLanguageVal", requestedLanguage);
        }
        return q;
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
    public List<FacetedSearchResult> facetedTermSearch(@Nonnull Collection<SearchParam> searchParams,
                                                       @Nonnull Pageable pageSpec) {
        Objects.requireNonNull(searchParams);
        Objects.requireNonNull(pageSpec);
        LOG.trace("Running faceted term search for search parameters: {}", searchParams);

        final List<SearchParam> relationshipAnnotationParams = searchParams.stream()
                .filter(p -> p.getProperty().toString().equals(Vocabulary.s_p_as_relationship))
                .toList();
        final List<SearchParam> regularParams = searchParams.stream()
                .filter(p -> !p.getProperty().toString().equals(Vocabulary.s_p_as_relationship))
                .toList();

        final StringBuilder queryStr = new StringBuilder(
                "SELECT DISTINCT ?t WHERE { ?t a ?term ; ?hasLabel ?label .\n");

        int i = 0;

        for (SearchParam p : relationshipAnnotationParams) {
            queryStr.append(buildRelationshipAnnotationQuery(p, i++));
        }

        for (SearchParam p : regularParams) {
            final String variable = "?v" + i++;
            queryStr.append("?t ").append(Utils.uriToString(p.getProperty())).append(" ").append(variable)
                    .append(" . ");
            switch (p.getMatchType()) {
                case IRI:
                    queryStr.append("FILTER (").append(variable).append(" IN (")
                            .append(p.getValue().stream().map(v -> Utils.uriToString(URI.create(v.toString()))).collect(
                                    Collectors.joining(","))).append("))\n");
                    break;
                case EXACT_MATCH:
                    // This also handles datatypes, as we transform the variable value to string and compare it with
                    // a string representation of the parameter value (e.g., "true" for Boolean true)
                    queryStr.append("FILTER (STR(").append(variable).append(") = \"")
                            .append(p.getValue().iterator().next().toString()).append("\")\n");
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

    /**
     * Builds a SPARQL query fragment for searching terms by relationship annotations.
     * <p>
     * Searches for terms that are subjects or objects in relationships annotated with the specified values.
     * Uses RDF-star syntax to query annotated triples.
     *
     * @param param Search parameter with annotation values
     * @param variableIndex Index for generating unique variable names
     * @return SPARQL query fragment
     */
    private String buildRelationshipAnnotationQuery(SearchParam param, int variableIndex) {
        final List<CustomAttribute> annotationProperties = dataDao.findAllCustomAttributesByDomain(
                URI.create(RDF.STATEMENT));

        if (annotationProperties.isEmpty()) {
            LOG.debug("No custom attributes with domain rdf:Statement found for relationship annotation search");
            return "";
        }

        final StringBuilder sb = new StringBuilder();
        final String valueVar = "?v" + variableIndex;
        final String subjectVar = "?s" + variableIndex;
        final String predicateVar = "?p" + variableIndex;
        final String objectVar = "?o" + variableIndex;
        final String annotationPropVar = "?ap" + variableIndex;

        final String annotationPropertiesFilter = annotationProperties.stream()
                .map(ap -> Utils.uriToString(ap.getUri()))
                .collect(Collectors.joining(","));

        sb.append("{\n");
        sb.append("  { << ?t ").append(predicateVar).append(" ").append(objectVar).append(" >> ")
          .append(annotationPropVar).append(" ").append(valueVar).append(" . }\n");
        sb.append("  UNION\n");
        sb.append("  { << ").append(subjectVar).append(" ").append(predicateVar).append(" ?t >> ")
          .append(annotationPropVar).append(" ").append(valueVar).append(" .\n");
        sb.append("    FILTER NOT EXISTS { ").append(subjectVar).append(" a ?snapshot . }\n");
        sb.append("  }\n");
        sb.append("}\n");

        sb.append("FILTER (").append(annotationPropVar).append(" IN (").append(annotationPropertiesFilter).append("))\n");

        if (param.getMatchType() == MatchType.IRI) {
            sb.append("FILTER (").append(valueVar).append(" IN (")
              .append(param.getValue().stream()
                      .map(v -> Utils.uriToString(URI.create(v.toString())))
                      .collect(Collectors.joining(","))).append("))\n");
        } else {
            sb.append("FILTER (STR(").append(valueVar).append(") = \"")
              .append(param.getValue().iterator().next().toString()).append("\")\n");
        }

        return sb.toString();
    }
}
