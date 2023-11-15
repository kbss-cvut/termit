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
package cz.cvut.kbss.termit.persistence.context;

import cz.cvut.kbss.termit.workspace.EditableVocabularies;

import java.net.URI;
import java.util.Optional;

/**
 * {@link VocabularyContextMapper} implementation based on editable vocabularies.
 * <p>
 * When resolving vocabulary context, it is checked whether the specified vocabulary is edited. If so, the context is
 * resolved via {@link EditableVocabularies}, as the vocabulary will be an editable copy stored in a different context.
 */
public class WorkspaceVocabularyContextMapper implements VocabularyContextMapper {

    private final VocabularyContextMapper delegatee;

    private final EditableVocabularies editableVocabularies;

    public WorkspaceVocabularyContextMapper(VocabularyContextMapper delegatee,
                                            EditableVocabularies editableVocabularies) {
        this.delegatee = delegatee;
        this.editableVocabularies = editableVocabularies;
    }

    @Override
    public URI getVocabularyContext(URI vocabularyUri) {
        return editableVocabularies.getVocabularyContext(vocabularyUri)
                                   .orElseGet(() -> delegatee.getVocabularyContext(vocabularyUri));
    }

    @Override
    public Optional<URI> getVocabularyInContext(URI contextUri) {
        return delegatee.getVocabularyInContext(contextUri);
    }
}
