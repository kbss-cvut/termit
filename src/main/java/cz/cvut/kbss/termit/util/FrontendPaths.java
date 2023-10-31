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
package cz.cvut.kbss.termit.util;

/**
 * Constants for navigation in TermIt UI.
 * <p>
 * Note that most paths are patterns containing variables to be replaced with suitable values. These variables are
 * enclosed in {@literal {}}.
 */
public class FrontendPaths {

    /**
     * Path to term detail.
     * <p>
     * Contains three variable for replacement:
     * <ul>
     *     <li>vocabularyName</li>
     *     <li>termName</li>
     *     <li>vocabularyNamespace</li>
     * </ul>
     */
    public static final String TERM_PATH = "/vocabularies/{vocabularyName}/terms/{termName}";

    /**
     * Query parameter indicating the tab to open in the asset detail UI.
     */
    public static final String ACTIVE_TAB_PARAM = "activeTab";

    /**
     * Identifier of the comments tab in the asset detail UI.
     */
    public static final String COMMENTS_TAB = "comments.title";

    /**
     * Path to vocabulary detail.
     * <p>
     * Contains two variables for replacement:
     * <ul>
     *      <li>vocabularyName</li>
     *      <li>vocabularyNamespace</li>
     * </ul>
     */
    public static final String VOCABULARY_PATH = "/vocabularies/{vocabularyName}";

    private FrontendPaths() {
        throw new AssertionError();
    }
}
