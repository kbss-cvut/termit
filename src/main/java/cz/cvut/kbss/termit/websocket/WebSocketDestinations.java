package cz.cvut.kbss.termit.websocket;

public final class WebSocketDestinations {

    /**
     * Used for publishing results of validation from server to clients
     */
    public static final String VOCABULARIES_VALIDATION = "/vocabularies/validation";

    private static final String VOCABULARIES_TEXT_ANALYSIS_FINISHED = "/vocabularies/text_analysis/finished";

    /**
     * Used for notifying clients about a text analysis end
     */
    public static final String VOCABULARIES_TEXT_ANALYSIS_FINISHED_FILE = VOCABULARIES_TEXT_ANALYSIS_FINISHED + "/file";

    /**
     * Used for notifying clients about a text analysis end
     */
    public static final String VOCABULARIES_TEXT_ANALYSIS_FINISHED_TERM_DEFINITION = VOCABULARIES_TEXT_ANALYSIS_FINISHED + "/term-definition";

    private WebSocketDestinations() {
        throw new AssertionError();
    }
}
