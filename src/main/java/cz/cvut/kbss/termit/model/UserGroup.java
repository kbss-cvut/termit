package cz.cvut.kbss.termit.model;

import cz.cvut.kbss.jopa.model.annotations.*;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.termit.util.Utils;
import cz.cvut.kbss.termit.util.Vocabulary;

import jakarta.validation.constraints.NotBlank;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@OWLClass(iri = Vocabulary.s_c_Usergroup)
public class UserGroup extends AccessControlAgent {

    /**
     * Namespace of UserGroup identifiers.
     */
    public static final String NAMESPACE = "http://rdfs.org/sioc/ns#";

    @NotBlank
    @ParticipationConstraints(nonEmpty = true)
    @OWLAnnotationProperty(iri = DC.Terms.TITLE)
    private String label;

    @OWLObjectProperty(iri = Vocabulary.s_p_has_member_A, fetch = FetchType.EAGER)
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
