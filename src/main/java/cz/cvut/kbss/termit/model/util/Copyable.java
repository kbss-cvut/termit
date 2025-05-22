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
package cz.cvut.kbss.termit.model.util;

/**
 * This interface is used to mark classes that can create a copy of themselves.
 *
 * @param <T> Type of the object
 */
public interface Copyable<T extends HasIdentifier> {

    /**
     * Creates a copy of this instance, copying the attribute values.
     * <p>
     * Note that the identifier should not be copied into the new instance, so that it can be persisted.
     *
     * @return New instance with values copied from this one
     */
    T copy();
}
