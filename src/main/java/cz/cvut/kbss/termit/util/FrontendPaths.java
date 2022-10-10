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
    public static final String TERM_PATH = "/vocabularies/{vocabularyName}/terms/{termName}?namespace={vocabularyNamespace}";

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
    public static final String VOCABULARY_PATH = "/vocabularies/{vocabularyName}?namespace={vocabularyNamespace}";

    private FrontendPaths() {
        throw new AssertionError();
    }
}
