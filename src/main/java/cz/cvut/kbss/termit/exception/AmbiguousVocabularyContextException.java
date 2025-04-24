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
package cz.cvut.kbss.termit.exception;

/**
 * Indicates a mismatch between vocabularies and repository contexts.
 * <p>
 * This may be either that versions of a single vocabulary were found in multiple contexts, and it was not possible to
 * determine which one to use. Or that multiple vocabularies were found in the same context.
 */
public class AmbiguousVocabularyContextException extends TermItException {

    public AmbiguousVocabularyContextException(String message) {
        super(message);
    }
}
