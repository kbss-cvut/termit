/**
 * TermIt
 * Copyright (C) 2019 Czech Technical University in Prague
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
package cz.cvut.kbss.termit.dto.assignment;

import cz.cvut.kbss.jopa.model.annotations.*;
import cz.cvut.kbss.jopa.vocabulary.RDFS;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.net.URI;
import java.util.Objects;

/**
 * Represents information about Term assignment to a Resource.
 * <p>
 * This provides only basic information about the Term - its identifier, label and identifier of a vocabulary to which
 * it belongs.
 */
@SparqlResultSetMapping(name = "ResourceTermAssignments", classes = @ConstructorResult(
        targetClass = ResourceTermAssignments.class,
        variables = {
                @VariableResult(name = "term", type = URI.class),
                @VariableResult(name = "label", type = String.class),
                @VariableResult(name = "vocabulary", type = URI.class),
                @VariableResult(name = "res", type = URI.class),
                @VariableResult(name = "suggested", type = Boolean.class)
        }
))
public class ResourceTermAssignments extends AbstractAssignmentsInfo {

    @OWLAnnotationProperty(iri = RDFS.LABEL)
    private String termLabel;

    @OWLObjectProperty(iri = Vocabulary.s_p_je_pojmem_ze_slovniku)
    private URI vocabulary;

    public ResourceTermAssignments() {
    }

    public ResourceTermAssignments(URI term, String termLabel, URI vocabulary, URI resource, Boolean suggested) {
        super(term, resource);
        this.termLabel = termLabel;
        this.vocabulary = vocabulary;
        addType(Vocabulary.s_c_prirazeni_termu);
        if (suggested) {
            addType(Vocabulary.s_c_navrzene_prirazeni_termu);
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ResourceTermAssignments)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        ResourceTermAssignments that = (ResourceTermAssignments) o;
        return Objects.equals(termLabel, that.termLabel) &&
                Objects.equals(vocabulary, that.vocabulary);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), termLabel, vocabulary);
    }

    @Override
    public String toString() {
        return "ResourceTermAssignments{" +
                super.toString() +
                ", termLabel='" + termLabel + '\'' +
                ", vocabulary=" + vocabulary +
                '}';
    }
}
