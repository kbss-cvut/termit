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
package cz.cvut.kbss.termit.persistence.context;

import cz.cvut.kbss.termit.util.Vocabulary;

/**
 * Declares static repository contexts.
 * <p>
 * That is, repository contexts that are not dependent on individual instances but are rather derived, for example, from
 * an entity class.
 */
public class StaticContexts {

    /**
     * Repository context for storing {@link cz.cvut.kbss.termit.model.UserGroup} instances.
     */
    public static final String USER_GROUPS = Vocabulary.s_c_sioc_Usergroup;

    /**
     * Repository context for storing {@link cz.cvut.kbss.termit.model.acl.AccessControlList} instances.
     */
    public static final String ACCESS_CONTROL_LISTS = Vocabulary.s_c_seznam_rizeni_pristupu;

    private StaticContexts() {
        throw new AssertionError();
    }
}
