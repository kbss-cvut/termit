package cz.cvut.kbss.termit.persistence.dao.util;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.ontodriver.model.LangString;
import cz.cvut.kbss.termit.dto.TermInfo;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps results of a SPARQL query to {@link TermInfo} instances.
 * <p>
 * The only reason this mapper exists is that {@link cz.cvut.kbss.jopa.model.annotations.SparqlResultSetMapping} does
 * not support plural values, which is what happens when multiple translations of a Term's label are loaded by the
 * query.
 */
public class SparqlResultToTermInfoMapper implements SparqlResultMapper<TermInfo> {

    private final Map<URI, TermInfo> visited = new LinkedHashMap<>();

    @Override
    public List<TermInfo> map(List<?> result) {
        for (Object elem : result) {
            final Object[] row = (Object[]) elem;
            if (row[0] == null) {
                // No result
                continue;
            }
            assert row.length == 3;
            final URI uri = (URI) row[0];
            final LangString ls = (LangString) row[1];
            TermInfo ti;
            if (visited.containsKey(uri)) {
                ti = visited.get(uri);
                ti.getLabel().set(ls.getLanguage().orElse(null), ls.getValue());
            } else {
                ti = new TermInfo(uri);
                visited.put(uri, ti);
                ti.setLabel(MultilingualString.create(ls.getValue(), ls.getLanguage().orElse(null)));
            }
            ti.setVocabulary((URI) row[2]);
            visited.put(ti.getUri(), ti);
        }
        return new ArrayList<>(visited.values());
    }
}
