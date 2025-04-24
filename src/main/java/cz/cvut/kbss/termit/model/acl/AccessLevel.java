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

import cz.cvut.kbss.jopa.model.annotations.Individual;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.util.Set;

/**
 * Levels of access to an asset.
 * <p>
 * The access levels are hierarchical, i.e., higher levels of access include lower (more restricted) levels.
 * <p>
 * Note that the order of the constants in this enum is significant and represents the level hierarchy, i.e., constants
 * with higher ordinal number represent higher access level.
 */
public enum AccessLevel {
    /**
     * The most restricted access level. The asset is not even visible to the user.
     */
    @Individual(iri = Vocabulary.s_i_zadna)
    NONE(Vocabulary.s_i_zadna),
    /**
     * Read access to an asset. May include exporting, commenting, or snapshot display.
     */
    @Individual(iri = Vocabulary.s_i_cteni)
    READ(Vocabulary.s_i_cteni, NONE),
    /**
     * Write access to an asset. The user can edit the asset.
     */
    @Individual(iri = Vocabulary.s_i_zapis)
    WRITE(Vocabulary.s_i_zapis, NONE, READ),
    /**
     * User can edit or remove an asset and manage access of other users/user groups to it.
     */
    @Individual(iri = Vocabulary.s_i_sprava)
    SECURITY(Vocabulary.s_i_sprava, NONE, READ, WRITE);

    private final String iri;

    private final Set<AccessLevel> included;

    AccessLevel(String iri, AccessLevel... included) {
        this.iri = iri;
        this.included = Set.of(included);
    }

    public boolean includes(AccessLevel requested) {
        return this == requested || included.contains(requested);
    }

    public String getIri() {
        return iri;
    }
}
