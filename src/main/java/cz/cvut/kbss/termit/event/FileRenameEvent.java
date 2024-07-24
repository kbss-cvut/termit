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
package cz.cvut.kbss.termit.event;

import cz.cvut.kbss.termit.model.resource.File;
import org.springframework.context.ApplicationEvent;

import java.util.Objects;

/**
 * Indicates that a {@link cz.cvut.kbss.termit.model.resource.File} asset has changed its label.
 */
public class FileRenameEvent extends ApplicationEvent {

    private final String originalName;

    private final String newName;

    public FileRenameEvent(File source, String originalName, String newName) {
        super(source);
        this.originalName = Objects.requireNonNull(originalName);
        this.newName = Objects.requireNonNull(newName);
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getNewName() {
        return newName;
    }

    @Override
    public File getSource() {
        return (File) super.getSource();
    }
}
