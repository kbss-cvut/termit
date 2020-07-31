/**
 * TermIt
 * Copyright (C) 2019 Czech Technical University in Prague
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
package cz.cvut.kbss.termit.exception.vocabularyremoval;

import cz.cvut.kbss.termit.exception.TermItException;

/**
 * Indicates that an error occurred in the vocabulary import relationship.
 */
public class DocumentVocabularyRemovalException extends TermItException {

    private final String messageId;

    public DocumentVocabularyRemovalException() {
        super("Removal of document vocabularies is not supported yet.");
        this.messageId = null;
    }

    public String getMessageId() {
        return messageId;
    }
}
