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
import cz.cvut.kbss.jopa.model.query.TypedQuery;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.jopa.vocabulary.RDF;
import cz.cvut.kbss.jopa.vocabulary.RDFS;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.search.MatchType;
import cz.cvut.kbss.termit.dto.search.SearchParam;
import cz.cvut.kbss.termit.dto.search.SearchResult;
import cz.cvut.kbss.termit.dto.search.SearchString;
import cz.cvut.kbss.termit.model.CustomAttribute;
import cz.cvut.kbss.termit.persistence.dao.spec.CustomAttributeSpecifications;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.Utils;
import cz.cvut.kbss.termit.util.Vocabulary;
import jakarta.annotation.Nonnull;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
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

    private static final Logger LOG = LoggerFactory.getLogger(SearchDao.class);

    static final char LUCENE_WILDCARD = '*';

    private final EntityManager em;
    private final DataDao dataDao;
    private String ftsQuery;
    private String ftsResultCountQuery;

    public SearchDao(EntityManager em, DataDao dataDao) {
        this.em = em;
        this.dataDao = dataDao;
    }

    @PostConstruct
    void loadQueries() {
        this.ftsQuery = Utils.loadQuery("fulltextsearch.rq");
        this.ftsResultCountQuery = Utils.loadQuery("fulltextsearchResultCount.rq");
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
     * @param searchString        The string to search by for full-text search (with optional language)
     * @param searchParams        Search parameters (facets) to filter the results by
     * @param pageSpec            Specification of the page of results to return
     * @param allowedVocabularies Vocabularies that are accessible to the current user for reading
     * @return Page of matching results
     */
    public Page<SearchResult> advancedSearch(@Nonnull SearchString searchString,
                                             @Nonnull Collection<SearchParam> searchParams, Pageable pageSpec,
                                             Collection<URI> allowedVocabularies) {
        Objects.requireNonNull(searchParams);
        final boolean searchStringBlank = searchString.searchString().isBlank();
        if (searchStringBlank && searchParams.isEmpty()) {
            return Page.empty(pageSpec);
        }

        final Page<SearchResult> result;
        if (searchStringBlank) {
            result = advancedSearchNoFullText(searchParams, pageSpec, allowedVocabularies);
        } else {
            result = advancedSearchWithFullText(searchString, searchParams, pageSpec, allowedVocabularies);
        }
        return result;
    }

    private Page<SearchResult> advancedSearchWithFullText(SearchString searchString,
                                                          Collection<SearchParam> searchParams,
                                                          Pageable pageSpec, Collection<URI> allowedVocabularies) {
        final Query query = initFullTextSearchQuery(ftsQuery, searchString, searchParams, allowedVocabularies,
                                                    queryString -> em.createNativeQuery(queryString,
                                                                                        "FullTextSearchResult"));
        final String exactMatch = splitExactMatch(searchString.searchString());
        query.setParameter("splitExactMatch", exactMatch, null);
        query.setParameter("searchString", searchString.searchString(), null);

        if (pageSpec.isPaged()) {
            query.setFirstResult((int) pageSpec.getOffset());
            query.setMaxResults(pageSpec.getPageSize());
        }
        return new PageImpl<>(query.getResultList(), pageSpec,
                              getTotalFulltextResultCount(searchString, searchParams, allowedVocabularies));
    }

    private <T extends Query> T initFullTextSearchQuery(String baseQueryStr, SearchString searchString,
                                                        Collection<SearchParam> searchParams,
                                                        Collection<URI> allowedVocabularies,
                                                        Function<String, T> queryCreator) {
        String queryStr = adjustQueryForLanguage(baseQueryStr, searchString.language());
        final String filters = buildSearchParamConditions(searchParams);
        queryStr = queryStr.replace("#FACETED_SEARCH_FILTERS#", filters);
        final String wildcardString = addWildcard(searchString.searchString());

        T query = (T) setCommonQueryParams(queryCreator.apply(queryStr), allowedVocabularies)
                .setParameter("wildCardSearchString", wildcardString, null);
        String langSuffix = searchString.language() == null ? "" : searchString.language();
        URI labelIndex = URI.create(Constants.LUCENE_CONNECTOR_LABEL_INDEX_PREFIX + langSuffix);
        URI defcomIndex = URI.create(Constants.LUCENE_CONNECTOR_DEFCOM_INDEX_PREFIX + langSuffix);
        query.setParameter("label_index", labelIndex).setParameter("defcom_index", defcomIndex);
        if (searchString.language() != null) {
            query.setParameter("requestedLanguageVal", searchString.language());
        }
        return query;
    }

    private Long getTotalFulltextResultCount(SearchString searchString,
                                             Collection<SearchParam> searchParams,
                                             Collection<URI> allowedVocabularies) {
        final TypedQuery<Long> query = initFullTextSearchQuery(ftsResultCountQuery, searchString, searchParams,
                                                    allowedVocabularies,
                                                    queryStr -> em.createNativeQuery(queryStr, Long.class));
        return query.getSingleResult();
    }

    /**
     * Finds assets that satisfy the provided faceted search parameters, without applying full-text search.
     *
     * @param searchParams        Search parameters (facets) to filter the results by
     * @param pageSpec            Specification of the page of results to return
     * @param allowedVocabularies Vocabularies accessible for the search
     * @return List of matching results
     */
    private Page<SearchResult> advancedSearchNoFullText(Collection<SearchParam> searchParams,
                                                        Pageable pageSpec, Collection<URI> allowedVocabularies) {

        String queryStr = "SELECT DISTINCT ?entity" +
                " (GROUP_CONCAT(DISTINCT CONCAT(?label, \"@\", lang(?label)); SEPARATOR=\"" + Constants.GROUP_CONCAT_SEPARATOR + "\") AS ?label)" +
                " (GROUP_CONCAT(DISTINCT CONCAT(?description, \"@\", lang(?description)); SEPARATOR=\"" + Constants.GROUP_CONCAT_SEPARATOR + "\") AS ?description)" +
                " ?vocabularyUri ?state ?type WHERE { \n" +
                buildWhereCondition(searchParams) +
                "} GROUP BY ?entity ?vocabularyUri ?state ?type ORDER BY ?entity";

        Query nativeQuery = em.createNativeQuery(queryStr, "FacetedSearchResult");
        setCommonQueryParams(nativeQuery, allowedVocabularies);

        if (pageSpec.isPaged()) {
            nativeQuery.setFirstResult((int) pageSpec.getOffset());
            nativeQuery.setMaxResults(pageSpec.getPageSize());
        }

        return new PageImpl<>(nativeQuery.getResultList(), pageSpec,
                              getTotalResultCount(searchParams, allowedVocabularies));
    }

    private String buildWhereCondition(Collection<SearchParam> searchParams) {
        return "  ?entity a ?type . \n" +
                "  FILTER (?type = ?term || ?type = ?vocabulary) \n" +
                "  FILTER NOT EXISTS { ?entity a ?snapshot . } \n" +

                // Retrieve label and description based on asset type (Concept vs Vocabulary)
                "  { \n" +
                "    ?entity <" + SKOS.PREF_LABEL + "> ?label ; \n" +
                "            ?inVocabulary ?entityVocabulary . \n" +
                "    OPTIONAL { ?entity <" + SKOS.DEFINITION + "> ?description . } \n" +
                "    FILTER (?entityVocabulary IN (?allowedVocabularies))\n" +
                "  } UNION { \n" +
                "    ?entity <" + DC.Terms.TITLE + "> ?label . \n" +
                "    OPTIONAL { ?entity <" + DC.Terms.DESCRIPTION + "> ?description . } \n" +
                "    FILTER (?entity IN (?allowedVocabularies))\n" +
                "  } \n" +
                "  OPTIONAL { ?entity ?inVocabulary ?vocabularyUri . } \n" +
                "  OPTIONAL { ?entity ?hasState ?state . } \n" +
                buildSearchParamConditions(searchParams);
    }

    private static <T extends Query> T setCommonQueryParams(T q, Collection<URI> allowedVocabularies) {
        q.setParameter("term", URI.create(SKOS.CONCEPT))
         .setParameter("snapshot", URI.create(Vocabulary.s_c_verze_objektu))
         .setParameter("vocabulary", URI.create(Vocabulary.s_c_slovnik))
         .setParameter("inVocabulary", URI.create(SKOS.IN_SCHEME))
         .setParameter("hasState", URI.create(Vocabulary.s_p_ma_stav_pojmu))
         .setParameter("allowedVocabularies", allowedVocabularies);
        return q;
    }

    private long getTotalResultCount(Collection<SearchParam> searchParams, Collection<URI> allowedVocabularies) {
        String queryStr = "SELECT (COUNT(DISTINCT ?entity) AS ?cnt) WHERE { \n" +
                buildWhereCondition(searchParams) +
                "}";
        TypedQuery<Long> nativeQuery = em.createNativeQuery(queryStr, Long.class);
        setCommonQueryParams(nativeQuery, allowedVocabularies);
        return nativeQuery.getSingleResult();
    }

    private String buildSearchParamConditions(Collection<SearchParam> searchParams) {
        final List<SearchParam> relationshipAnnotationParams = searchParams.stream()
                                                                           .filter(p -> p.getProperty().toString()
                                                                                         .equals(Vocabulary.s_p_relationship))
                                                                           .toList();
        final List<SearchParam> regularParams = searchParams.stream()
                                                            .filter(p -> !p.getProperty().toString()
                                                                           .equals(Vocabulary.s_p_relationship))
                                                            .toList();

        final StringBuilder queryStr = new StringBuilder();

        int i = 0;

        for (SearchParam p : relationshipAnnotationParams) {
            queryStr.append(buildRelationshipAnnotationQuery(p, i++));
        }

        for (SearchParam p : regularParams) {
            final String variable = "?v" + i++;
            if (isExplicitNull(p)) {
                queryStr.append(buildNullFilter(p, variable)).append('\n');
                continue;
            }
            queryStr.append("?entity").append(" ").append(Utils.uriToString(p.getProperty())).append(" ")
                    .append(variable)
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

    private static boolean isExplicitNull(SearchParam p) {
        return (p.getMatchType() == MatchType.IRI || p.getMatchType() == MatchType.EXACT_MATCH) && p.getValue()
                                                                                                    .contains(RDF.NIL);
    }

    private static String buildNullFilter(SearchParam p, String variable) {
        if (URI.create(RDF.TYPE).equals(p.getProperty())) {
            return "FILTER NOT EXISTS { ?entity a " + variable + " . FILTER (" + variable + " NOT IN (?term, ?vocabulary, <" + RDFS.RESOURCE + ">))}";
        }
        return "FILTER NOT EXISTS { ?entity " + Utils.uriToString(p.getProperty()) + " [] }";
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
        sb.append("  { << ").append("?entity").append(" ").append(predicateVar).append(" ").append(objectVar)
          .append(" >> ")
          .append(annotationPropVar).append(" ").append(valueVar).append(" . }\n");
        sb.append("  UNION\n");
        sb.append("  { << ").append(subjectVar).append(" ").append(predicateVar).append(" ").append("?entity")
          .append(" >> ")
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
