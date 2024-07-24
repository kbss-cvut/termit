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

import com.fasterxml.jackson.annotation.JsonIgnore;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLDataProperty;
import cz.cvut.kbss.jopa.model.annotations.ParticipationConstraints;
import cz.cvut.kbss.jopa.model.annotations.Types;
import cz.cvut.kbss.termit.model.util.HasTypes;
import cz.cvut.kbss.termit.util.Vocabulary;

import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@OWLClass(iri = Vocabulary.s_c_uzivatel_termitu)
public class User extends AccessControlAgent implements HasTypes, Serializable {

    @NotBlank
    @ParticipationConstraints(nonEmpty = true)
    @OWLDataProperty(iri = cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_krestni_jmeno)
    String firstName;

    @NotBlank
    @ParticipationConstraints(nonEmpty = true)
    @OWLDataProperty(iri = cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_prijmeni)
    String lastName;

    @NotBlank
    @ParticipationConstraints(nonEmpty = true)
    @OWLDataProperty(iri = Vocabulary.s_p_ma_uzivatelske_jmeno)
    String username;

    @Types
    Set<String> types;

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @Override
    public Set<String> getTypes() {
        return types;
    }

    @Override
    public void setTypes(Set<String> types) {
        this.types = types;
    }

    public UserAccount toUserAccount() {
        final UserAccount account = new UserAccount();
        account.setUri(getUri());
        account.setFirstName(firstName);
        account.setLastName(lastName);
        account.setUsername(username);
        if (types != null) {
            account.setTypes(new HashSet<>(types));
        }
        return account;
    }

    @JsonIgnore
    public String getFullName() {
        return firstName + " " + lastName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof User that)) {
            return false;
        }
        return Objects.equals(getUri(), that.getUri()) && Objects.equals(username, that.username);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getUri(), username);
    }

    @Override
    public String toString() {
        return "User{" +
                getFullName() +
                ", username='" + username + '\'' +
                '}';
    }
}
