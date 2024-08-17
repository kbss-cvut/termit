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

import org.springframework.core.io.FileSystemResource;

import java.io.File;
import java.util.Objects;
import java.util.Optional;

/**
 * File system resource aware of its type.
 */
public class TypeAwareFileSystemResource extends FileSystemResource implements TypeAwareResource {

    private final String mediaType;

    public TypeAwareFileSystemResource(File file, String mediaType) {
        super(file);
        this.mediaType = mediaType;
    }

    @Override
    public Optional<String> getMediaType() {
        return Optional.ofNullable(mediaType);
    }

    @Override
    public Optional<String> getFileExtension() {
        assert getFilename() != null;
        return getFilename().contains(".") ? Optional.of(getFilename().substring(getFilename().lastIndexOf("."))) :
               Optional.empty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TypeAwareFileSystemResource that)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        return Objects.equals(mediaType, that.mediaType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mediaType);
    }
}
