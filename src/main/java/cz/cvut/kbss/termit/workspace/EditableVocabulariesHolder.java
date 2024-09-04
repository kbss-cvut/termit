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
package cz.cvut.kbss.termit.workspace;

import org.springframework.web.context.annotation.SessionScope;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Holds editable vocabularies for a session (if applicable).
 */
@SessionScope
public class EditableVocabulariesHolder {

    /**
     * Mapping of editable vocabulary identifiers to contexts in which the editable version is stored.
     */
    private final Map<URI, URI> editableVocabularies = new HashMap<>();

    void registerEditableVocabulary(URI vocabularyUri, URI contextUri) {
        editableVocabularies.put(vocabularyUri, contextUri);
    }

    void clear() {
        editableVocabularies.clear();
    }

    boolean hasRegisteredVocabulary(URI vocabularyUri) {
        return editableVocabularies.containsKey(vocabularyUri);
    }

    Optional<URI> getVocabularyContext(URI vocabularyUri) {
        return Optional.ofNullable(editableVocabularies.get(vocabularyUri));
    }

    Set<URI> getRegisteredContexts() {
        return new HashSet<>(editableVocabularies.values());
    }

    /**
     * This read-only holder should be used in cases where no session is available.
     * <p>
     * For example, when on-startup tasks require access to vocabularies. In that case, all vocabularies are accessible.
     * However, note that care should be taken not to use this holder for user-based actions like triggering text
     * analysis of a vocabulary's terms.
     */
    static class SharedEditableVocabulariesHolder extends EditableVocabulariesHolder {

        @Override
        void registerEditableVocabulary(URI vocabularyUri, URI contextUri) {
            // Do nothing
        }

        @Override
        boolean hasRegisteredVocabulary(URI vocabularyUri) {
            return false;
        }

        @Override
        Optional<URI> getVocabularyContext(URI vocabularyUri) {
            return Optional.empty();
        }
    }
}
