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
