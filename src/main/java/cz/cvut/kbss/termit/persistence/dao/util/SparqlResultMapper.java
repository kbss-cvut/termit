package cz.cvut.kbss.termit.persistence.dao.util;

import java.util.List;

/**
 * Maps results of a SPARQL native query to the target type
 * @param <T> Target type
 */
@FunctionalInterface
public interface SparqlResultMapper<T> {

    List<T> map(List<?> result);
}
