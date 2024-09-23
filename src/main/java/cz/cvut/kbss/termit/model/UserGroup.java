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
package cz.cvut.kbss.termit.model;

import cz.cvut.kbss.jopa.model.annotations.FetchType;
import cz.cvut.kbss.jopa.model.annotations.OWLAnnotationProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.model.annotations.ParticipationConstraints;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.termit.util.Utils;
import cz.cvut.kbss.termit.util.Vocabulary;
import jakarta.validation.constraints.NotBlank;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@OWLClass(iri = Vocabulary.s_c_sioc_Usergroup)
public class UserGroup extends AccessControlAgent {

    /**
     * Namespace of UserGroup identifiers.
     */
    public static final String NAMESPACE = Vocabulary.ONTOLOGY_IRI_SIOC;

    @NotBlank
    @ParticipationConstraints(nonEmpty = true)
    @OWLAnnotationProperty(iri = DC.Terms.TITLE)
    private String label;

    @OWLObjectProperty(iri = Vocabulary.s_p_sioc_has_member, fetch = FetchType.EAGER)
    private Set<User> members;

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Set<User> getMembers() {
        return members;
    }

    public void setMembers(Set<User> members) {
        this.members = members;
    }

    public void addMember(User member) {
        Objects.requireNonNull(member);
        if (members == null) {
            this.members = new HashSet<>();
        }
        members.add(member);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        UserGroup userGroup = (UserGroup) o;
        return Objects.equals(getUri(), userGroup.getUri()) && Objects.equals(label, userGroup.label);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getUri(), label);
    }

    @Override
    public String toString() {
        return "UserGroup{" +
                label + Utils.uriToString(getUri()) +
                ", member count = " + Utils.emptyIfNull(members).size() +
                '}';
    }
}
