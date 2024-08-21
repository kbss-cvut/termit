package cz.cvut.kbss.termit.util.throttle;

import java.net.URI;

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
}
