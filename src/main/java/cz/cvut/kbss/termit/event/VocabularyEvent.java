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

import jakarta.annotation.Nonnull;
import org.springframework.context.ApplicationEvent;

import java.net.URI;
import java.util.Objects;

/**
 * Base class for vocabulary related events
 */
public abstract class VocabularyEvent extends ApplicationEvent {
    protected final URI vocabularyIri;

    protected VocabularyEvent(@Nonnull Object source, @Nonnull URI vocabularyIri) {
        super(source);
        Objects.requireNonNull(vocabularyIri);
        this.vocabularyIri = vocabularyIri;
    }

    /**
     * The identifier of the vocabulary to which this event is bound
     * @return vocabulary IRI
     */
    public URI getVocabularyIri() {
        return vocabularyIri;
    }
}
