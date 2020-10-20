package cz.cvut.kbss.termit.dto.statistics;

import cz.cvut.kbss.jopa.model.annotations.*;
import cz.cvut.kbss.jopa.vocabulary.RDFS;

import java.io.Serializable;
import java.net.URI;

@SparqlResultSetMapping(name = "TermFrequencyDto", classes = {
        @ConstructorResult(targetClass = TermFrequencyDto.class,
                variables = {
                        @VariableResult(name = "vocabulary", type = URI.class),
                        @VariableResult(name = "count", type = Integer.class),
                        @VariableResult(name = "label", type = String.class)
                })})
public class TermFrequencyDto implements Serializable {

    @Id
    private URI id;

    @OWLDataProperty(iri = "http://onto.fel.cvut.cz/ontologies/application/termit/pojem/has-count")
    private Integer count;

    @OWLDataProperty(iri = RDFS.LABEL)
    private String label;

    public TermFrequencyDto(URI vocabulary, Integer count, String label) {
        this.id = vocabulary;
        this.count = count;
        this.label = label;
    }

    public URI getId() {
        return id;
    }

    public void setId(URI id) {
        this.id = id;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
