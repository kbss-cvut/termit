package cz.cvut.kbss.termit.dto.search;

import cz.cvut.kbss.termit.util.Utils;

import java.net.URI;

/**
 * Parameter of the faceted term search.
 */
public class SearchParam {

    private URI property;

    private String value;

    private MatchType matchType;

    public URI getProperty() {
        return property;
    }

    public void setProperty(URI property) {
        this.property = property;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public MatchType getMatchType() {
        return matchType;
    }

    public void setMatchType(MatchType matchType) {
        this.matchType = matchType;
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
