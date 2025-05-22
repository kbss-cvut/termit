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
package cz.cvut.kbss.termit.asset.provenance;

/**
 * Indicates that last modification date of assets is tracked by this class.
 */
public interface SupportsLastModification {

    /**
     * Gets timestamp of the last modification of assets managed by this class.
     *
     * @return Timestamp of last modification in millis since epoch
     */
    long getLastModified();

    /**
     * Refreshes the last modified value.
     * <p>
     * This method is required only for implementations which actually store the last modified value. Those which act
     * only as delegates need not implement it.
     */
    default void refreshLastModified() {
    }
}
