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

import cz.cvut.kbss.jopa.model.annotations.ConstructorResult;
import cz.cvut.kbss.jopa.model.annotations.OWLAnnotationProperty;
import cz.cvut.kbss.jopa.model.annotations.SparqlResultSetMapping;
import cz.cvut.kbss.jopa.model.annotations.VariableResult;
import cz.cvut.kbss.jopa.vocabulary.RDFS;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.net.URI;
import java.util.Objects;

/**
 * Represents Term assignment to a Resource.
 * <p>
 * It provides only basic data about the Resource to which a Term is assigned.
 */
@SparqlResultSetMapping(name = "TermAssignments", classes = @ConstructorResult(
        targetClass = TermAssignments.class,
        variables = {
                @VariableResult(name = "term", type = URI.class),
                @VariableResult(name = "resource", type = URI.class),
                @VariableResult(name = "label", type = String.class),
                @VariableResult(name = "suggested", type = Boolean.class)
        }
))
public class TermAssignments extends AbstractAssignmentsInfo {

    @OWLAnnotationProperty(iri = RDFS.LABEL)
    private String resourceLabel;

    public TermAssignments() {
    }

    public TermAssignments(URI term, URI resource, String resourceLabel, Boolean suggested) {
        super(term, resource);
        this.resourceLabel = resourceLabel;
        addType(Vocabulary.s_c_prirazeni_termu);
        if (suggested) {
            addType(Vocabulary.s_c_navrzene_prirazeni_termu);
        }
    }

    public String getResourceLabel() {
        return resourceLabel;
    }

    public void setResourceLabel(String resourceLabel) {
        this.resourceLabel = resourceLabel;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TermAssignments)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        TermAssignments that = (TermAssignments) o;
        return Objects.equals(resourceLabel, that.resourceLabel);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), resourceLabel);
    }

    @Override
    public String toString() {
        return "TermAssignments{" +
                super.toString() +
                "resourceLabel='" + resourceLabel + "\'}";
    }
}
