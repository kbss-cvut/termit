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
package cz.cvut.kbss.termit.util.throttle;

import java.net.URI;

/**
 * Provides static methods allowing construction of dynamic group identifiers
 * used in {@link Throttle @Throttle} annotations.
 */
@SuppressWarnings("unused") // it is used from SpEL expressions
public class ThrottleGroupProvider {

    private ThrottleGroupProvider() {
        throw new AssertionError();
    }

    private static final String TEXT_ANALYSIS_VOCABULARIES = "TEXT_ANALYSIS_VOCABULARIES";

    public static String getTextAnalysisVocabulariesAll() {
        return TEXT_ANALYSIS_VOCABULARIES;
    }

    public static String getTextAnalysisVocabularyAllTerms(URI vocabulary) {
        return TEXT_ANALYSIS_VOCABULARIES + "_" + vocabulary;
    }

    public static String getTextAnalysisVocabularyTerm(URI vocabulary, URI term) {
        return TEXT_ANALYSIS_VOCABULARIES + "_" + vocabulary + "_" + term;
    }
}
