package cz.cvut.kbss.termit.dto.filter;

import java.net.URI;

/**
 * Represents parameters for filtering vocabulary content changes.
 */
public class VocabularyContentChangeFilterDto {
    private String termName;
    private String changedAttributeName;
    private String authorName;
    private URI changeType;

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
}
