package cz.cvut.kbss.termit.dto.filter;

import java.net.URI;
import java.util.Objects;

/**
 * Represents parameters for filtering vocabulary content changes.
 */
public class VocabularyContentChangeFilterDto {
    private String termName = "";
    private String changedAttributeName = "";
    private String authorName = "";
    private URI changeType = null;

    public String getTermName() {
        return termName;
    }

    public void setTermName(String termName) {
        this.termName = termName;
    }

    public String getChangedAttributeName() {
        return changedAttributeName;
    }

    public void setChangedAttributeName(String changedAttributeName) {
        this.changedAttributeName = changedAttributeName;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public URI getChangeType() {
        return changeType;
    }

    public void setChangeType(URI changeType) {
        this.changeType = changeType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VocabularyContentChangeFilterDto that)) return false;
        return Objects.equals(termName, that.termName) &&
                Objects.equals(changedAttributeName, that.changedAttributeName) &&
                Objects.equals(authorName, that.authorName) &&
                Objects.equals(changeType, that.changeType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(termName, changedAttributeName, authorName, changeType);
    }
}
