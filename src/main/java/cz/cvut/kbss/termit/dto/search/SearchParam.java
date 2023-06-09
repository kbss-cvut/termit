package cz.cvut.kbss.termit.dto.search;

import cz.cvut.kbss.termit.exception.ValidationException;
import cz.cvut.kbss.termit.util.Utils;

import java.net.URI;
import java.util.Objects;
import java.util.Set;

/**
 * Parameter of the faceted term search.
 */
public class SearchParam {

    private URI property;

    private Set<String> value;

    private MatchType matchType = MatchType.EXACT_MATCH;

    public SearchParam() {
    }

    // For test purposes
    public SearchParam(URI property, String value, MatchType matchType) {
        this.property = Objects.requireNonNull(property);
        this.value = Set.of(value);
        this.matchType = Objects.requireNonNull(matchType);
    }

    public URI getProperty() {
        return property;
    }

    public void setProperty(URI property) {
        this.property = property;
    }

    public Set<String> getValue() {
        return value;
    }

    public void setValue(Set<String> value) {
        this.value = value;
    }

    public MatchType getMatchType() {
        return matchType;
    }

    public void setMatchType(MatchType matchType) {
        this.matchType = matchType;
    }

    /**
     * Validates this search parameter.
     * <p>
     * This mainly means checking that the values correspond to the match type, e.g., that a single value is provided
     * for string-matching types.
     */
    public void validate() {
        if (Utils.emptyIfNull(value).isEmpty() || property == null) {
            throw new ValidationException("Must provide a property and value to search by!");
        }
        if (matchType != MatchType.IRI && Utils.emptyIfNull(value).size() != 1) {
            throw new ValidationException("Exactly one value must be provided for match type " + matchType);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SearchParam)) {
            return false;
        }
        SearchParam that = (SearchParam) o;
        return Objects.equals(property, that.property)
                && Objects.equals(value, that.value) && matchType == that.matchType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(property, value, matchType);
    }

    @Override
    public String toString() {
        return "SearchParam{" +
                "property=" + Utils.uriToString(property) +
                ", value='" + value + '\'' +
                ", matchType=" + matchType +
                '}';
    }
}
