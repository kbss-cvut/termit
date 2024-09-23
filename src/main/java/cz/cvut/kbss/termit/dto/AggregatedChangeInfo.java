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
package cz.cvut.kbss.termit.dto;

import cz.cvut.kbss.jopa.model.annotations.ConstructorResult;
import cz.cvut.kbss.jopa.model.annotations.OWLDataProperty;
import cz.cvut.kbss.jopa.model.annotations.SparqlResultSetMapping;
import cz.cvut.kbss.jopa.model.annotations.Types;
import cz.cvut.kbss.jopa.model.annotations.VariableResult;
import cz.cvut.kbss.termit.model.util.HasTypes;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;

/**
 * Aggregated information about change records on a specific date.
 */
@SparqlResultSetMapping(name = "AggregatedChangeInfo",
                        classes = {@ConstructorResult(targetClass = AggregatedChangeInfo.class,
                                                      variables = {
                                                              @VariableResult(name = "date", type = String.class),
                                                              @VariableResult(name = "cnt", type = BigInteger.class)
                                                      })})
public class AggregatedChangeInfo implements HasTypes, Comparable<AggregatedChangeInfo> {

    @OWLDataProperty(iri = Vocabulary.s_p_ma_datum_a_cas_modifikace)
    private LocalDate date;

    /**
     * Number of distinct changed assets
     */
    @OWLDataProperty(iri = Vocabulary.s_p_totalItems)
    private Integer count;

    @Types
    private Set<String> types;

    public AggregatedChangeInfo() {
    }

    public AggregatedChangeInfo(String date, BigInteger count) {
        this.date = LocalDate.parse(date);
        this.count = count.intValueExact(); // We do not expect the value not to fit in int
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public Set<String> getTypes() {
        return types;
    }

    public void setTypes(Set<String> types) {
        this.types = types;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AggregatedChangeInfo that = (AggregatedChangeInfo) o;
        return Objects.equals(date, that.date) && Objects.equals(count, that.count) && Objects.equals(types, that.types);
    }

    @Override
    public int hashCode() {
        return Objects.hash(date, count, types);
    }

    @Override
    public String toString() {
        return "AggregatedChangeInfo{" +
                "count=" + count +
                ", date=" + date +
                ", types='" + types + '\'' +
                '}';
    }

    @Override
    public int compareTo(AggregatedChangeInfo other) {
        assert date != null;
        assert other.date != null;

        final int dateRes = date.compareTo(other.date);
        return dateRes != 0 ? dateRes : hasType(Vocabulary.s_c_vytvoreni_entity) ? -1 : 1;
    }
}
