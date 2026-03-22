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
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.jopa.vocabulary.RDF;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.search.FullTextSearchResult;
import cz.cvut.kbss.termit.dto.search.MatchType;
import cz.cvut.kbss.termit.dto.search.SearchParam;
import cz.cvut.kbss.termit.model.CustomAttribute;
import cz.cvut.kbss.termit.persistence.dao.spec.CustomAttributeSpecifications;
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
        final String[] split = searchString.trim().split("\\s+");
        split[split.length - 1] += LUCENE_WILDCARD;
        return String.join(" ", split);
    }

    private static String splitExactMatch(String searchString) {
        final String[] split = searchString.trim().split("\\s+");
        String s = "<em>";
        return s.concat(String.join("</em> <em>", split)).concat("</em>");
    }

    /**
     * Finds assets that match the specified search string and satisfy the provided faceted search parameters.
     * <p>
     * This combines full-text search with faceted filtering.
     *
     * @param searchString The string to search by for full-text search.
     * @param language     The language of the {@code searchString}, {@code null} to match all languages
     * @param searchParams Search parameters (facets) to filter the results by
     * @param pageSpec     Specification of the page of results to return
     * @return List of matching results
     */
    public List<FullTextSearchResult> advancedSearch(@Nonnull String searchString, @Nullable String language,
                                                     @Nonnull Collection<SearchParam> searchParams, Pageable pageSpec) {
        Objects.requireNonNull(searchParams);
        if (searchString.isBlank() && searchParams.isEmpty()) {
            return Collections.emptyList();
        }

        if (searchString.isBlank()) {
            return advancedSearchNoFullText(language, searchParams, pageSpec);
        }

        String query = adjustQueryForLanguage(ftsQuery, language);
        final String filters = buildSearchParamConditions(searchParams);
        final int lastBraceIndex = query.lastIndexOf("}");
        if (lastBraceIndex != -1) {
            query = query.substring(0, lastBraceIndex) + filters + query.substring(lastBraceIndex);
        } else {
            LOG.warn("Unable to inject facet parameters into FTS query, '}' not found.");
        }

        final String wildcardString = searchString.isBlank() ? "*" : addWildcard(searchString);
        final String exactMatch = searchString.isBlank() ? "" : splitExactMatch(searchString);
        Query nativeQuery = setCommonQueryParams(em.createNativeQuery(query, "FullTextSearchResult"),
                                                 searchString, language)
                .setParameter("snapshot", URI.create(Vocabulary.s_c_verze_objektu))
                .setParameter("wildCardSearchString", wildcardString, null)
                .setParameter("splitExactMatch", exactMatch, null);

        if (pageSpec.isPaged()) {
            nativeQuery.setFirstResult((int) pageSpec.getOffset());
            nativeQuery.setMaxResults(pageSpec.getPageSize());
        }

        return nativeQuery.getResultList();
    }

    /**
     * Finds assets that satisfy the provided faceted search parameters, without applying full-text search.
     *
     * @param language The language to filter by for label and definition, or null to include all languages
     * @param searchParams Search parameters (facets) to filter the results by
     * @param pageSpec Specification of the page of results to return
     * @return List of matching results
     */
    private List<FullTextSearchResult> advancedSearchNoFullText(@Nullable String language,
                                                                @Nonnull Collection<SearchParam> searchParams,
                                                                @Nonnull Pageable pageSpec) {
        final StringBuilder queryStr = new StringBuilder();
        queryStr.append("SELECT DISTINCT ?entity ?label ?description ?vocabularyUri ?state ?type ?snippetField ?snippetText ?score WHERE { \n");
        queryStr.append("  ?entity a ?type . \n");
        queryStr.append("  FILTER (?type = ?term || ?type = ?vocabulary) \n");
        queryStr.append("  FILTER NOT EXISTS { ?entity a ?snapshot . } \n");

        // Retrieve label and description based on asset type (Concept vs Vocabulary)
        queryStr.append("  { \n");
        queryStr.append("    ?entity <").append(SKOS.PREF_LABEL).append("> ?label . \n");
        queryStr.append("    OPTIONAL { ?entity <").append(SKOS.DEFINITION).append("> ?description . } \n");
        queryStr.append("    BIND(<").append(SKOS.PREF_LABEL).append("> AS ?snippetField) \n");
        queryStr.append("    BIND(?label AS ?snippetText) \n");
        queryStr.append("  } UNION { \n");
        queryStr.append("    ?entity <").append(DC.Terms.TITLE).append("> ?label . \n");
        queryStr.append("    OPTIONAL { ?entity <").append(DC.Terms.DESCRIPTION).append("> ?description . } \n");
        queryStr.append("    BIND(<").append(DC.Terms.TITLE).append("> AS ?snippetField) \n");
        queryStr.append("    BIND(?label AS ?snippetText) \n");
        queryStr.append("  } \n");

        queryStr.append("  OPTIONAL { ?entity ?inVocabulary ?vocabularyUri . } \n");
        queryStr.append("  OPTIONAL { ?entity ?hasState ?state . } \n");
        queryStr.append("  BIND(0.0 AS ?score) \n");

        if (language != null) {
            queryStr.append("  FILTER (lang(?label) = ?requestedLanguage || lang(?label) = \"\") \n");
        }

        queryStr.append(buildSearchParamConditions(searchParams));
        queryStr.append("} ORDER BY ?label");

        Query nativeQuery = em.createNativeQuery(queryStr.toString(), "FullTextSearchResult")
                              .setParameter("snapshot", URI.create(Vocabulary.s_c_verze_objektu))
                              .setParameter("term", URI.create(SKOS.CONCEPT))
                              .setParameter("vocabulary", URI.create(Vocabulary.s_c_slovnik))
                              .setParameter("inVocabulary", URI.create(Vocabulary.s_p_je_pojmem_ze_slovniku))
                              .setParameter("hasState", URI.create(Vocabulary.s_p_ma_stav_pojmu));

        if (language != null) {
            nativeQuery.setParameter("requestedLanguage", language);
        }

        if (pageSpec.isPaged()) {
            nativeQuery.setFirstResult((int) pageSpec.getOffset());
            nativeQuery.setMaxResults(pageSpec.getPageSize());
        }

        return nativeQuery.getResultList();
    }

    protected Query setCommonQueryParams(Query q, String searchString, String requestedLanguage) {
        String langSuffix = requestedLanguage == null ? "" : requestedLanguage;
        URI labelIndex = URI.create(Constants.LUCENE_CONNECTOR_LABEL_INDEX_PREFIX + langSuffix);
        URI defcomIndex = URI.create(Constants.LUCENE_CONNECTOR_DEFCOM_INDEX_PREFIX + langSuffix);
        q.setParameter("label_index", labelIndex).setParameter("defcom_index", defcomIndex);
        q.setParameter("term", URI.create(SKOS.CONCEPT))
         .setParameter("vocabulary", URI.create(Vocabulary.s_c_slovnik));
        q.setParameter("inVocabulary", URI.create(Vocabulary.s_p_je_pojmem_ze_slovniku))
         .setParameter("hasState", URI.create(Vocabulary.s_p_ma_stav_pojmu))
         .setParameter("searchString", searchString, null);
        if (requestedLanguage != null) {
            q.setParameter("requestedLanguageVal", requestedLanguage);
        }
        return q;
    }

    private String buildSearchParamConditions(Collection<SearchParam> searchParams) {
        final List<SearchParam> relationshipAnnotationParams = searchParams.stream()
                                                                           .filter(p -> p.getProperty().toString()
                                                                                         .equals(Vocabulary.s_p_as_relationship))
                                                                           .toList();
        final List<SearchParam> regularParams = searchParams.stream()
                                                            .filter(p -> !p.getProperty().toString()
                                                                           .equals(Vocabulary.s_p_as_relationship))
                                                            .toList();

        final StringBuilder queryStr = new StringBuilder();

        int i = 0;

        for (SearchParam p : relationshipAnnotationParams) {
            queryStr.append(buildRelationshipAnnotationQuery(p, i++));
        }

        for (SearchParam p : regularParams) {
            final String variable = "?v" + i++;
            queryStr.append("?entity").append(" ").append(Utils.uriToString(p.getProperty())).append(" ").append(variable)
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
        return queryStr.toString();
    }

    /**
     * Builds a SPARQL query fragment for searching terms by relationship annotations.
     * <p>
     * Searches for terms that are subjects or objects in relationships annotated with the specified values. Uses
     * RDF-star syntax to query annotated triples.
     *
     * @param param         Search parameter with annotation values
     * @param variableIndex Index for generating unique variable names
     * @return SPARQL query fragment
     */
    private String buildRelationshipAnnotationQuery(SearchParam param, int variableIndex) {
        final List<CustomAttribute> annotationProperties = dataDao.findAllCustomAttributes(List.of(
                CustomAttributeSpecifications.hasDomain(URI.create(RDF.STATEMENT))));

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
        sb.append("  { << ").append("?entity").append(" ").append(predicateVar).append(" ").append(objectVar).append(" >> ")
          .append(annotationPropVar).append(" ").append(valueVar).append(" . }\n");
        sb.append("  UNION\n");
        sb.append("  { << ").append(subjectVar).append(" ").append(predicateVar).append(" ").append("?entity").append(" >> ")
          .append(annotationPropVar).append(" ").append(valueVar).append(" .\n");
        sb.append("    FILTER NOT EXISTS { ").append(subjectVar).append(" a ?snapshot . }\n");
        sb.append("  }\n");
        sb.append("}\n");

        sb.append("FILTER (").append(annotationPropVar).append(" IN (").append(annotationPropertiesFilter)
          .append("))\n");

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
