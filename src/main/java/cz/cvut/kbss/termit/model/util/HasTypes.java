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
package cz.cvut.kbss.termit.model.util;

import cz.cvut.kbss.termit.util.Utils;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Implemented by entities with {@link cz.cvut.kbss.jopa.model.annotations.Types} attribute.
 * <p>
 * Adds default implementation for adding and removing types.
 */
public interface HasTypes {

    Set<String> getTypes();

    void setTypes(Set<String> types);

    /**
     * Adds the specified type to this instance's types.
     *
     * @param type Type to add
     */
    default void addType(String type) {
        Objects.requireNonNull(type);
        if (getTypes() == null) {
            setTypes(new HashSet<>(2));
        }
        getTypes().add(type);
    }

    /**
     * Removes the specified type from this instance's types.
     * <p>
     * If the type is not present, this is a no-op.
     *
     * @param type Type to remove
     */
    default void removeType(String type) {
        Objects.requireNonNull(type);
        if (getTypes() == null) {
            return;
        }
        getTypes().remove(type);
    }

    /**
     * Checks whether this instance has the specified type.
     * @param type Type to check for
     * @return {@code true} if the type is present on this instance, {@code false} otherwise
     */
    default boolean hasType(String type) {
        return Utils.emptyIfNull(getTypes()).contains(type);
    }
}
