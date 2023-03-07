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
package cz.cvut.kbss.termit.rest.dto;

import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLDataProperty;
import cz.cvut.kbss.jopa.model.annotations.util.NonEntity;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.util.Objects;

/**
 * DTO used for user updating so that original password can be validated.
 */
@NonEntity
@OWLClass(iri = Vocabulary.s_c_uzivatel_termitu)
public class UserUpdateDto extends UserAccount {

    @OWLDataProperty(iri = Vocabulary.ONTOLOGY_IRI_termit + "/original-password")
    private String originalPassword;

    public String getOriginalPassword() {
        return originalPassword;
    }

    public void setOriginalPassword(String originalPassword) {
        this.originalPassword = originalPassword;
    }

    /**
     * Transforms this DTO to the regular entity.
     * <p>
     * This is necessary for correct persistence processing, as this class is not a known entity class.
     *
     * @return {@link User} instance
     */
    public UserAccount asUserAccount() {
        final UserAccount user = new UserAccount();
        user.setUri(getUri());
        user.setFirstName(getFirstName());
        user.setLastName(getLastName());
        user.setUsername(getUsername());
        user.setPassword(getPassword());
        user.setTypes(getTypes());
        return user;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserUpdateDto)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        UserUpdateDto that = (UserUpdateDto) o;
        return Objects.equals(originalPassword, that.originalPassword);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), originalPassword);
    }
}
