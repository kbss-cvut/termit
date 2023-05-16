package cz.cvut.kbss.termit.rest.doc;

/**
 * Common constants for the Open API documentation of the system REST API.
 */
public class ApiDocConstants {

    /**
     * Description of the {@link cz.cvut.kbss.termit.util.Constants.QueryParams#PAGE_SIZE} query parameter.
     */
    public static final String PAGE_SIZE_DESCRIPTION = "Number of items to retrieve.";

    /**
     * Description of the {@link cz.cvut.kbss.termit.util.Constants.QueryParams#PAGE} query parameter.
     */
    public static final String PAGE_NO_DESCRIPTION = "Page number.";

    /**
     * Example of a ISO-formatted datetime accepted by the API when specifying datetime range.
     */
    public static final String DATETIME_EXAMPLE = "2023-01-01T00:00:00";

    private ApiDocConstants() {
        throw new AssertionError();
    }
}
