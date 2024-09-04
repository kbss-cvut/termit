/*
 * TermIt
 * Copyright (C) 2023 Czech Technical University in Prague
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
package cz.cvut.kbss.termit.persistence.dao.util;

import cz.cvut.kbss.termit.model.assignment.DefinitionalOccurrenceTarget;
import cz.cvut.kbss.termit.model.assignment.FileOccurrenceTarget;
import cz.cvut.kbss.termit.model.assignment.TermDefinitionalOccurrence;
import cz.cvut.kbss.termit.model.assignment.TermFileOccurrence;
import cz.cvut.kbss.termit.model.assignment.TermOccurrence;
import cz.cvut.kbss.termit.model.selector.TextPositionSelector;
import cz.cvut.kbss.termit.model.selector.TextQuoteSelector;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps SPARQL query results to {@link  TermOccurrence} instances.
 * <p>
 * This helper mapper has been introduced because the default behavior of JOPA tends to cause issues when the number of
 * query results is large (thousands of occurrences) and many other relevant instances (target, selectors) have to be
 * retrieved.
 * <p>
 * In those cases, the repository (GraphDB in particular) starts to refuse requests, probably due to their high number
 * in a short period of time.
 * <p>
 * This class thus allows to take results of a single query which loads everything and map them to the Java objects.
 */
public class SparqlResultToTermOccurrenceMapper implements SparqlResultMapper<TermOccurrence> {

    private final URI source;

    private final Map<URI, TermOccurrence> visited = new LinkedHashMap<>();

    public SparqlResultToTermOccurrenceMapper(URI source) {
        this.source = source;
    }

    /**
     * Maps the specified list of SPARQL query results to term occurrences.
     * <p>
     * The query result rows are expected to contain the following:
     * <pre>
     *     <ol>
     *         <li>occurrence IRI</li>
     *         <li>occurrence type - file occurrence or definitional occurrence</li>
     *         <li>occurring term IRI</li>
     *         <li>target IRI</li>
     *         <li>whether the occurrence is suggested or not (boolean)</li>
     *         <li>selector IRI</li>
     *         <li>exact match string in case of a text quote selector</li>
     *         <li>match prefix in case of a text quote selector, optional</li>
     *         <li>match suffix in case of a text quote selector, optional</li>
     *         <li>occurrence starting position in case of a text position selector</li>
     *         <li>occurrence end position in case of a text position selector</li>
     *     </ol>
     * </pre>
     *
     * @param queryResult SPARQL query result list
     * @return List of {@code TermOccurrence}s
     */
    @Override
    public List<TermOccurrence> map(List<?> queryResult) {
        for (Object item : queryResult) {
            final Object[] row = (Object[]) item;
            if (row[0] == null) {
                // No result
                continue;
            }
            assert row.length == 11;
            final URI occurrenceId = (URI) row[0];
            final TermOccurrence occurrence =
                    visited.containsKey(occurrenceId) ? visited.get(occurrenceId) : createOccurrence(row);
            resolveSelector(row, occurrence);
        }
        return new ArrayList<>(visited.values());
    }

    private TermOccurrence createOccurrence(final Object[] row) {
        final TermOccurrence occurrence;
        if (Vocabulary.s_c_souborovy_vyskyt_termu.equals(row[1].toString())) {
            occurrence = new TermFileOccurrence((URI) row[2], new FileOccurrenceTarget());
        } else {
            occurrence = new TermDefinitionalOccurrence((URI) row[2], new DefinitionalOccurrenceTarget());
        }
        occurrence.setUri((URI) row[0]);
        occurrence.getTarget().setUri((URI) row[3]);
        occurrence.getTarget().setSource(source);
        occurrence.getTarget().setSelectors(new HashSet<>());
        if ((boolean) row[4]) {
            occurrence.addType(Vocabulary.s_c_navrzeny_vyskyt_termu);
        }
        visited.put(occurrence.getUri(), occurrence);
        return occurrence;
    }

    private void resolveSelector(final Object[] row, TermOccurrence occurrence) {
        if (row[6] != null) {
            final TextQuoteSelector selector = new TextQuoteSelector(row[6].toString());
            selector.setUri((URI) row[5]);
            selector.setPrefix(row[7] != null ? row[7].toString() : null);
            selector.setSuffix(row[8] != null ? row[8].toString() : null);
            occurrence.getTarget().getSelectors().add(selector);
        } else {
            assert row[9] != null && row[10] != null;
            final TextPositionSelector selector = new TextPositionSelector();
            selector.setUri((URI) row[5]);
            selector.setStart((Integer) row[9]);
            selector.setEnd((Integer) row[10]);
            occurrence.getTarget().getSelectors().add(selector);
        }
    }
}
