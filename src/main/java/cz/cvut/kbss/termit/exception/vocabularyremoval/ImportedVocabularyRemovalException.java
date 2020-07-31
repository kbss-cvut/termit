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
import cz.cvut.kbss.termit.model.Vocabulary;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Indicates that an error occurred in the vocabulary import relationship.
 */
public class ImportedVocabularyRemovalException extends TermItException {

    private final String messageId;

    public ImportedVocabularyRemovalException(List<Vocabulary> vocabularies) {
        super("Vocabulary cannot be removed. It is referenced from other vocabularies: "
            + vocabularies.stream().map( v -> v.getLabel()).collect(
            Collectors.joining(", ")));
        this.messageId = null;
    }

    public String getMessageId() {
        return messageId;
    }
}
