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
package cz.cvut.kbss.termit.event;

import cz.cvut.kbss.termit.model.resource.File;
import jakarta.annotation.Nonnull;

import java.net.URI;

/**
 * Indicates that text analysis of a file was finished
 */
public class FileTextAnalysisFinishedEvent extends VocabularyEvent {

    private final URI fileUri;

    public FileTextAnalysisFinishedEvent(Object source, @Nonnull File file) {
        super(source, file.getDocument().getVocabulary());
        this.fileUri = file.getUri();
    }

    public URI getFileUri() {
        return fileUri;
    }
}
