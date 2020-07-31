/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If not, see
 * <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLDataProperty;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.util.HashSet;

@OWLClass(iri = Vocabulary.s_c_uzivatel)
public class UserAccount extends AbstractUser {

    @OWLDataProperty(iri = Vocabulary.s_p_ma_heslo)
    private String password;

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Erases the password in this instance.
     * <p>
     * This should be used for security reasons when passing the instance throughout the application and especially when
     * it to be send from the REST API to the client.
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
    public boolean isAdmin() {
        return types != null && types.contains(Vocabulary.s_c_administrator_termitu);
    }

    /**
     * Transforms this security-related {@code UserAccount} instance to a domain-specific {@code User} instance.
     *
     * @return new user instance based on this account
     */
    public User toUser() {
        final User user = new User();
        copyAttributes(user);
        return user;
    }

    protected void copyAttributes(AbstractUser target) {
        target.setUri(uri);
        target.setFirstName(firstName);
        target.setLastName(lastName);
        target.setUsername(username);
        if (types != null) {
            target.setTypes(new HashSet<>(types));
        }
    }

    /**
     * Returns a copy of this user account.
     *
     * @return This instance's copy
     */
    public UserAccount copy() {
        final UserAccount clone = new UserAccount();
        copyAttributes(clone);
        clone.password = password;
        return clone;
    }
}
