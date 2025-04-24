/*
 * TermIt
 * Copyright (C) 2025 Czech Technical University in Prague
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
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
