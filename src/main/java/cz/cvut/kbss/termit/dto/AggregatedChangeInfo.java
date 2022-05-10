package cz.cvut.kbss.termit.dto;

import cz.cvut.kbss.jopa.model.annotations.*;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.math.BigInteger;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Aggregated information about change records on a specific date.
 */
@SparqlResultSetMapping(name = "AggregatedChangeInfo",
                        classes = {@ConstructorResult(targetClass = AggregatedChangeInfo.class,
                                                      variables = {
                                                              @VariableResult(name = "date", type = String.class),
                                                              @VariableResult(name = "cnt", type = BigInteger.class)
                                                      })})
public class AggregatedChangeInfo implements Comparable<AggregatedChangeInfo> {

    @OWLDataProperty(iri = Vocabulary.s_p_ma_datum_a_cas_modifikace)
    private LocalDate date;

    @OWLDataProperty(iri = Vocabulary.s_p_totalItems)
    private Integer count;

    @Types
    private String type;

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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
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
        return Objects.equals(date, that.date) && Objects.equals(count, that.count) && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(date, count, type);
    }

    @Override
    public String toString() {
        return "AggregatedChangeInfo{" +
                "count=" + count +
                ", date=" + date +
                ", type='" + type + '\'' +
                '}';
    }

    @Override
    public int compareTo(AggregatedChangeInfo other) {
        final int dateRes = date.compareTo(other.date);
        return dateRes != 0 ? dateRes : Vocabulary.s_c_vytvoreni_entity.equals(type) ? -1 : 1;
    }
}
