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
package cz.cvut.kbss.termit.model.acl;

import com.fasterxml.jackson.annotation.JsonIgnore;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.util.Objects;
import java.util.Optional;

@OWLClass(iri = Vocabulary.s_c_zaznam_rizeni_pristupu_uzivatele)
public class UserAccessControlRecord extends AccessControlRecord<User> {

    public UserAccessControlRecord() {
    }

    public UserAccessControlRecord(AccessLevel accessLevel, User holder) {
        super(accessLevel, holder);
    }

    @JsonIgnore
    @Override
    public Optional<AccessLevel> getAccessLevelFor(UserAccount user) {
        Objects.requireNonNull(user);
        assert getHolder() != null;

        return getHolder().getUri().equals(user.getUri()) ? Optional.of(getAccessLevel()) : Optional.empty();
    }

    @Override
    public UserAccessControlRecord copy() {
        return new UserAccessControlRecord(getAccessLevel(), getHolder());
    }
}
