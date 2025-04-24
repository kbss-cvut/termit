/*
 * TermIt
 * Copyright (C) 2025 Czech Technical University in Prague
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

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import cz.cvut.kbss.jopa.model.annotations.EnumType;
import cz.cvut.kbss.jopa.model.annotations.Enumerated;
import cz.cvut.kbss.jopa.model.annotations.FetchType;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.termit.model.AbstractEntity;
import cz.cvut.kbss.termit.model.AccessControlAgent;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.util.Objects;
import java.util.Optional;

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "className")
@OWLClass(iri = Vocabulary.s_c_zaznam_rizeni_pristupu)
public abstract class AccessControlRecord<T extends AccessControlAgent> extends AbstractEntity {

    @Enumerated(EnumType.OBJECT_ONE_OF)
    @OWLObjectProperty(iri = Vocabulary.s_p_ma_uroven_pristupovych_opravneni)
    private AccessLevel accessLevel;

    @OWLObjectProperty(iri = Vocabulary.s_p_ma_drzitele_pristupovych_opravneni, fetch = FetchType.EAGER)
    private T holder;

    public AccessControlRecord() {
    }

    public AccessControlRecord(AccessLevel accessLevel, T holder) {
        this.accessLevel = accessLevel;
        this.holder = holder;
    }

    public AccessLevel getAccessLevel() {
        return accessLevel;
    }

    public void setAccessLevel(AccessLevel accessLevel) {
        this.accessLevel = accessLevel;
    }

    public T getHolder() {
        return holder;
    }

    public void setHolder(T holder) {
        this.holder = holder;
    }

    /**
     * Gets access level for the specified user account, if possible.
     * <p>
     * If this record does not have any information about access level for the specified user, an empty {@link Optional}
     * is returned.
     *
     * @param user User to get access level for
     * @return Optional access level relevant for the specified user
     */
    public abstract Optional<AccessLevel> getAccessLevelFor(UserAccount user);

    /**
     * Creates a copy of this record, initializing it with the same holder and access level.
     * <p>
     * The identifier is not copied.
     *
     * @return Copy of this instance
     */
    public abstract AccessControlRecord<T> copy();

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AccessControlRecord<?> that)) {
            return false;
        }
        return accessLevel == that.accessLevel && Objects.equals(holder, that.holder);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accessLevel, holder);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                holder + " -> " + getAccessLevel() +
                '}';
    }
}
