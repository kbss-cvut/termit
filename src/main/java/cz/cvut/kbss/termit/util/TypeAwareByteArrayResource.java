/*
 * TermIt
 * Copyright (C) 2023 Czech Technical University in Prague
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
package cz.cvut.kbss.termit.util;

import org.springframework.core.io.ByteArrayResource;

import java.util.Objects;
import java.util.Optional;

/**
 * Adds support for media type and the associated file extension awareness to {@link ByteArrayResource}.
 */
public class TypeAwareByteArrayResource extends ByteArrayResource implements TypeAwareResource {

    private final String mediaType;
    private final String fileExtension;

    public TypeAwareByteArrayResource(byte[] byteArray, String mediaType, String fileExtension) {
        super(byteArray);
        this.mediaType = mediaType;
        this.fileExtension = fileExtension;
    }

    @Override
    public Optional<String> getMediaType() {
        return Optional.ofNullable(mediaType);
    }

    @Override
    public Optional<String> getFileExtension() {
        return Optional.ofNullable(fileExtension);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TypeAwareByteArrayResource that)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        return Objects.equals(mediaType, that.mediaType) &&
                Objects.equals(fileExtension, that.fileExtension);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mediaType, fileExtension);
    }
}
