package cz.cvut.kbss.termit.dto.search;

/**
 * Describes how the property value should be matched in the data.
 */
public enum MatchType {
    /**
     * Matches resource identifier in the repository.
     */
    IRI,
    /**
     * Matches the specified value as a substring of the string representation of a property value in the repository.
     */
    SUBSTRING,
    /**
     * Matches the specified value exactly to the string representation of a property value in the repository.
     */
    EXACT_MATCH
}
