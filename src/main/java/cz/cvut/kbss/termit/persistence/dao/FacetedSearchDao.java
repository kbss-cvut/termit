package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.search.FacetedSearchResult;
import cz.cvut.kbss.termit.dto.search.SearchParam;
import cz.cvut.kbss.termit.util.Utils;
import cz.cvut.kbss.termit.util.Vocabulary;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Base search dao providing faceted search
 */
public class FacetedSearchDao {
    private static final Logger LOG = LoggerFactory.getLogger(FacetedSearchDao.class);
    protected final EntityManager em;

    protected FacetedSearchDao(EntityManager em) {
        this.em = em;
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
