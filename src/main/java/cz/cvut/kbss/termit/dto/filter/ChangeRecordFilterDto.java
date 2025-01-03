package cz.cvut.kbss.termit.dto.filter;

import com.fasterxml.jackson.annotation.JsonIgnore;
import cz.cvut.kbss.termit.util.Utils;

import java.net.URI;
import java.util.Objects;

/**
 * Represents parameters for filtering vocabulary content changes.
 */
public class ChangeRecordFilterDto {
    private String assetLabel = "";
    private String changedAttributeName = "";
    private String authorName = "";
    private URI changeType = null;

    public ChangeRecordFilterDto() {
    }

    public ChangeRecordFilterDto(String changedAttributeName, String authorName, URI changeType) {
        this.changedAttributeName = changedAttributeName;
        this.authorName = authorName;
        this.changeType = changeType;
    }

    public ChangeRecordFilterDto(String assetLabel, String changedAttributeName, String authorName, URI changeType) {
        this.assetLabel = assetLabel;
        this.changedAttributeName = changedAttributeName;
        this.authorName = authorName;
        this.changeType = changeType;
    }

    public String getAssetLabel() {
        return assetLabel;
    }

    public void setAssetLabel(String assetLabel) {
        this.assetLabel = assetLabel;
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

    /**
     * @return true when all attributes are empty or null
     */
    @JsonIgnore
    public boolean isEmpty() {
        return Utils.isBlank(assetLabel) &&
                Utils.isBlank(changedAttributeName) &&
                Utils.isBlank(authorName) &&
                changeType == null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChangeRecordFilterDto that)) return false;
        return Objects.equals(assetLabel, that.assetLabel) &&
                Objects.equals(changedAttributeName, that.changedAttributeName) &&
                Objects.equals(authorName, that.authorName) &&
                Objects.equals(changeType, that.changeType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(assetLabel, changedAttributeName, authorName, changeType);
    }


    /**
     * Constants for the Open API documentation of the REST API.
     */
    public static final class ApiDoc {
        public static final String TERM_NAME_DESCRIPTION = "Name of the term used for filtering.";
        public static final String CHANGE_TYPE_DESCRIPTION = "Type of the change used for filtering.";
        public static final String AUTHOR_NAME_DESCRIPTION = "Name of the author of the change used for filtering.";
        public static final String CHANGED_ATTRIBUTE_DESCRIPTION = "Name of the changed attribute used for filtering.";

        private ApiDoc() {
            throw new AssertionError();
        }
    }
}
