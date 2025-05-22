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
package cz.cvut.kbss.termit.persistence.context;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.workspace.EditableVocabularies;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkspaceVocabularyContextMapperTest {

    @Mock
    private VocabularyContextMapper delegatee;

    @Mock
    private EditableVocabularies editableVocabularies;

    @InjectMocks
    WorkspaceVocabularyContextMapper sut;

    @Test
    void getVocabularyContextUsesDelegateeWhenVocabularyIsNotAmongEditableVocabularies() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        when(editableVocabularies.getVocabularyContext(vocabulary.getUri())).thenReturn(Optional.empty());
        when(delegatee.getVocabularyContext(vocabulary.getUri())).thenReturn(vocabulary.getUri());

        final URI result = sut.getVocabularyContext(vocabulary.getUri());
        assertEquals(vocabulary.getUri(), result);
        verify(delegatee).getVocabularyContext(vocabulary.getUri());
    }

    @Test
    void getVocabularyContextReturnsEditedCopyContextWhenVocabularyIsEditable() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final URI editedCopyCtx = Generator.generateUri();
        when(editableVocabularies.getVocabularyContext(vocabulary.getUri())).thenReturn(Optional.of(editedCopyCtx));

        final URI result = sut.getVocabularyContext(vocabulary);
        assertEquals(editedCopyCtx, result);
        verify(delegatee, never()).getVocabularyContext(vocabulary);
        verify(delegatee, never()).getVocabularyContext(vocabulary.getUri());
        verify(editableVocabularies).getVocabularyContext(vocabulary.getUri());
    }

    @Test
    void getVocabularyInContextForwardsCallToDelegatee() {
        final URI context = Generator.generateUri();
        final URI vocabulary = Generator.generateUri();
        when(delegatee.getVocabularyInContext(context)).thenReturn(Optional.of(vocabulary));
        final Optional<URI> result = sut.getVocabularyInContext(context);
        assertTrue(result.isPresent());
        assertEquals(vocabulary, result.get());
        verify(delegatee).getVocabularyInContext(context);
    }
}
