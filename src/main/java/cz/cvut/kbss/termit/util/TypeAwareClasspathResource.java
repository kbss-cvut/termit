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
package cz.cvut.kbss.termit.util;

import org.springframework.core.io.ClassPathResource;

import java.util.Optional;

/**
 * Implementation of {@link TypeAwareResource} for files on classpath.
 */
public class TypeAwareClasspathResource extends ClassPathResource implements TypeAwareResource {

    private final String mediaType;

    public TypeAwareClasspathResource(String path, String mediaType) {
        super(path);
        this.mediaType = mediaType;
    }

    @Override
    public Optional<String> getMediaType() {
        return Optional.ofNullable(mediaType);
    }

    @Override
    public Optional<String> getFileExtension() {
        return getPath().contains(".") ? Optional.of(getPath().substring(getPath().lastIndexOf("."))) :
               Optional.empty();
    }
}
