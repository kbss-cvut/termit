package cz.cvut.kbss.termit.service.export;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Specifies configuration of vocabulary export.
 */
public class ExportConfig {

    private final ExportType type;

    private final String mediaType;

    private Set<String> referenceProperties = new HashSet<>();

    public ExportConfig(ExportType type, String mediaType) {
        this.type = Objects.requireNonNull(type);
        this.mediaType = Objects.requireNonNull(mediaType);
    }

    public ExportConfig(ExportType type, String mediaType, Set<String> referenceProperties) {
        this.type = type;
        this.mediaType = mediaType;
        this.referenceProperties = referenceProperties;
    }

    public ExportType getType() {
        return type;
    }

    public String getMediaType() {
        return mediaType;
    }

    public Set<String> getReferenceProperties() {
        return referenceProperties;
    }

    public void setReferenceProperties(Set<String> referenceProperties) {
        this.referenceProperties = referenceProperties;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ExportConfig that = (ExportConfig) o;
        return type == that.type && mediaType.equals(that.mediaType) && referenceProperties.equals(
                that.referenceProperties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, mediaType, referenceProperties);
    }

    @Override
    public String toString() {
        return "ExportConfig{" +
                "type=" + type +
                ", mediaType=" + mediaType +
                '}';
    }
}
