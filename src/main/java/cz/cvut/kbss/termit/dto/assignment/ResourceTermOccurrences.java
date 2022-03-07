/**
 * TermIt
 * Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.dto.assignment;

import cz.cvut.kbss.jopa.model.annotations.*;
import cz.cvut.kbss.jopa.vocabulary.RDFS;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.net.URI;
import java.util.Objects;

import static cz.cvut.kbss.termit.dto.assignment.TermOccurrences.COUNT_PROPERTY;

/**
 * Represents aggregated information about a Term occurring in a Resource.
 * <p>
 * It contains info about the Term - identifier, label, vocabulary identifier - and how many times it occurs in the
 * Resource.
 */
@SparqlResultSetMapping(name = "ResourceTermOccurrences", classes = @ConstructorResult(
        targetClass = ResourceTermOccurrences.class,
        variables = {
                @VariableResult(name = "term", type = URI.class),
                @VariableResult(name = "label", type = String.class),
                @VariableResult(name = "vocabulary", type = URI.class),
                @VariableResult(name = "res", type = URI.class),
                @VariableResult(name = "cnt", type = Integer.class),
                @VariableResult(name = "suggested", type = Boolean.class)
        }
))
public class ResourceTermOccurrences extends AbstractAssignmentInfo {

    @OWLAnnotationProperty(iri = RDFS.LABEL)
    private String termLabel;

    @OWLObjectProperty(iri = Vocabulary.s_p_je_pojmem_ze_slovniku)
    private URI vocabulary;

    @OWLDataProperty(iri = COUNT_PROPERTY)
    private Integer count;

    public ResourceTermOccurrences() {
    }

    public ResourceTermOccurrences(URI term, String termLabel, URI vocabulary, URI resource, Integer count,
                                   Boolean suggested) {
        super(term, resource);
        this.termLabel = termLabel;
        this.vocabulary = vocabulary;
        this.count = count;
        addType(Vocabulary.s_c_vyskyt_termu);
        if (suggested) {
            addType(Vocabulary.s_c_navrzeny_vyskyt_termu);
        }
    }

    public String getTermLabel() {
        return termLabel;
    }

    public void setTermLabel(String termLabel) {
        this.termLabel = termLabel;
    }

    public URI getVocabulary() {
        return vocabulary;
    }

    public void setVocabulary(URI vocabulary) {
        this.vocabulary = vocabulary;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ResourceTermOccurrences)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        ResourceTermOccurrences that = (ResourceTermOccurrences) o;
        return Objects.equals(termLabel, that.termLabel) && Objects.equals(vocabulary, that.vocabulary) && Objects.equals(count, that.count);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), termLabel, vocabulary, count);
    }

    @Override
    public String toString() {
        return "ResourceTermOccurrences{" +
                super.toString() +
                ", count=" + count +
                '}';
    }
}
