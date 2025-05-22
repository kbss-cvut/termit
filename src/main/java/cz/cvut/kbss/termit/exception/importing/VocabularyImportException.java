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
package cz.cvut.kbss.termit.exception.importing;

import cz.cvut.kbss.termit.exception.TermItException;

/**
 * Indicates that an error occurred in the vocabulary import procedure.
 */
public class VocabularyImportException extends TermItException {

    public VocabularyImportException(String message) {
        super(message);
    }

    public VocabularyImportException(String message, String messageId) {
        super(message, messageId);
    }

    public VocabularyImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
