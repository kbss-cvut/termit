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

import jakarta.annotation.Nonnull;

import java.net.URI;

/**
 * Represents an event of modification of the content of a vocabulary.
 * <p>
 * This typically means a term is added, removed or modified. Modification of vocabulary metadata themselves is not considered here.
 */
public class VocabularyContentModifiedEvent extends VocabularyEvent {

    public VocabularyContentModifiedEvent(@Nonnull Object source, @Nonnull URI vocabularyIri) {
        super(source, vocabularyIri);
    }
}
