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
package cz.cvut.kbss.termit.websocket;

public final class WebSocketDestinations {

    /**
     * Used for publishing results of validation from server to clients
     */
    public static final String VOCABULARIES_VALIDATION = "/vocabularies/validation";

    /**
     * Used for notifying client about a text analysis failure
     */
    public static final String VOCABULARIES_TEXT_ANALYSIS_FAILED = "/vocabularies/text_analysis/failed";

    /**
     * Used for notifying client about finishing a text analysis
     */
    private static final String VOCABULARIES_TEXT_ANALYSIS_FINISHED = "/vocabularies/text_analysis/finished";

    /**
     * Used for notifying clients about a text analysis end
     */
    public static final String VOCABULARIES_TEXT_ANALYSIS_FINISHED_FILE = VOCABULARIES_TEXT_ANALYSIS_FINISHED + "/file";

    /**
     * Used for notifying clients about a text analysis end
     */
    public static final String VOCABULARIES_TEXT_ANALYSIS_FINISHED_TERM_DEFINITION = VOCABULARIES_TEXT_ANALYSIS_FINISHED + "/term-definition";

    /**
     * Used for pushing updates about long-running tasks to clients
     */
    public static final String LONG_RUNNING_TASKS_UPDATE = "/long-running-tasks/update";

    private WebSocketDestinations() {
        throw new AssertionError();
    }
}
