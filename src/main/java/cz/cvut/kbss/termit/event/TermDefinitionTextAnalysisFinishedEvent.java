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

import cz.cvut.kbss.termit.model.AbstractTerm;
import jakarta.annotation.Nonnull;

import java.net.URI;

/**
 * Indicates that a text analysis of a term definition was finished
 */
public class TermDefinitionTextAnalysisFinishedEvent extends VocabularyEvent {
    private final URI termUri;

    public TermDefinitionTextAnalysisFinishedEvent(@Nonnull Object source, @Nonnull AbstractTerm term) {
        super(source, term.getVocabulary());
        this.termUri = term.getUri();
    }

    public URI getTermUri() {
        return termUri;
    }
}
