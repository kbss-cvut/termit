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
import cz.cvut.kbss.jopa.model.annotations.*;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.model.util.HasTypes;
import cz.cvut.kbss.termit.security.model.UserRole;
import cz.cvut.kbss.termit.util.Vocabulary;

import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
import java.net.URI;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@OWLClass(iri = Vocabulary.s_c_uzivatel_termitu)
public class UserAccount implements HasIdentifier, HasTypes, Serializable {

    @Id
    private URI uri;

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

    @OWLDataProperty(iri = Vocabulary.s_p_ma_heslo)
    private String password;

    @OWLDataProperty(iri = Vocabulary.s_p_sioc_last_activity_date)
    private Instant lastSeen;

    @Types
    Set<String> types;

    @Override
    public URI getUri() {
        return uri;
    }

    @Override
    public void setUri(URI uri) {
        this.uri = uri;
    }

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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Instant getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(Instant lastSeen) {
        this.lastSeen = lastSeen;
    }

    @Override
    public Set<String> getTypes() {
        return types;
    }

    @Override
    public void setTypes(Set<String> types) {
        this.types = types;
    }

    /**
     * Erases the password in this instance.
     * <p>
     * This should be used for security reasons when passing the instance throughout the application and especially when
     * it is to be sent from the REST API to the client.
     */
    public void erasePassword() {
        this.password = null;
    }

    /**
     * Checks whether the account represented by this instance is locked.
     *
     * @return Locked status
     */
    @JsonIgnore
    public boolean isLocked() {
        return types != null && types.contains(Vocabulary.s_c_uzamceny_uzivatel_termitu);
    }

    /**
     * Locks the account represented by this instance.
     */
    public void lock() {
        addType(Vocabulary.s_c_uzamceny_uzivatel_termitu);
    }

    /**
     * Unlocks the account represented by this instance.
     */
    public void unlock() {
        if (types == null) {
            return;
        }
        types.remove(Vocabulary.s_c_uzamceny_uzivatel_termitu);
    }

    /**
     * Enables the account represented by this instance.
     * <p>
     * Does nothing if the account is already enabled.
     */
    public void enable() {
        if (types == null) {
            return;
        }
        types.remove(Vocabulary.s_c_zablokovany_uzivatel_termitu);
    }

    /**
     * Checks whether the account represented by this instance is enabled.
     */
    @JsonIgnore
    public boolean isEnabled() {
        return types == null || !types.contains(Vocabulary.s_c_zablokovany_uzivatel_termitu);
    }

    /**
     * Disables the account represented by this instance.
     * <p>
     * Disabled account cannot be logged into and cannot be used to view/modify data.
     */
    public void disable() {
        addType(Vocabulary.s_c_zablokovany_uzivatel_termitu);
    }

    /**
     * Checks whether this account is administrator.
     *
     * @return {@code true} if this account is of administrator type
     */
    @JsonIgnore
    public boolean isAdmin() {
        return hasType(Vocabulary.s_c_administrator_termitu);
    }

    /**
     * Checks if this user account has the specified role.
     *
     * @param role Role to evaluate
     * @return {@code true} if this account has a type corresponding to the specified role, {@code false} otherwise
     */
    @JsonIgnore
    public boolean hasRole(UserRole role) {
        return hasType(role.getType());
    }

    /**
     * Transforms this security-related {@code UserAccount} instance to a domain-specific {@code User} instance.
     *
     * @return new user instance based on this account
     */
    public User toUser() {
        final User user = new User();
        user.setUri(getUri());
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setUsername(username);
        if (types != null) {
            user.setTypes(new HashSet<>(types));
        }
        return user;
    }

    /**
     * Returns a copy of this user account.
     *
     * @return This instance's copy
     */
    public UserAccount copy() {
        final UserAccount clone = new UserAccount();
        clone.setUri(getUri());
        clone.setFirstName(firstName);
        clone.setLastName(lastName);
        clone.setUsername(username);
        if (types != null) {
            clone.setTypes(new HashSet<>(types));
        }
        clone.password = password;
        clone.lastSeen = lastSeen;
        return clone;
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
        if (!(o instanceof UserAccount that)) {
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
        return "UserAccount{" + getFullName() + ", username='" + username + '\'' + '}';
    }
}
