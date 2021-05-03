package cz.cvut.kbss.termit.persistence.dao.util;

import java.util.List;

/**
 * Maps results of a SPARQL native query to the target type
 *
 * @param <T> Target type
 */
@FunctionalInterface
public interface SparqlResultMapper<T> {

    /**
     * Maps the specified SPARQL native query results to the target type instances.
     *
     * @param result SPARQL query result
     * @return Results transformed by this method
     */
    List<T> map(List<?> result);
}
