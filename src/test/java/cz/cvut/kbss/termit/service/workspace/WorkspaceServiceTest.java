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
package cz.cvut.kbss.termit.service.workspace;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.persistence.context.VocabularyContextMapper;
import cz.cvut.kbss.termit.workspace.EditableVocabularies;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkspaceServiceTest {

    @Mock
    private EditableVocabularies editableVocabularies;

    @Mock
    private VocabularyContextMapper contextMapper;

    @InjectMocks
    private WorkspaceService sut;

    @Test
    void openForEditingResolvesVocabulariesFromSpecifiedContextsAndRegistersThemAsEditable() {
        final Map<URI, URI> vocabularyToCtx = new HashMap<>();
        IntStream.range(0, 5).forEach(i -> vocabularyToCtx.put(Generator.generateUri(), Generator.generateUri()));
        vocabularyToCtx.forEach((k, v) -> when(contextMapper.getVocabularyInContext(v)).thenReturn(Optional.of(k)));

        sut.openForEditing(vocabularyToCtx.values());
        vocabularyToCtx.forEach((k, v) -> {
            verify(contextMapper).getVocabularyInContext(v);
            verify(editableVocabularies).registerEditableVocabulary(k, v);
        });
    }

    @Test
    void openForEditingThrowsNotFoundExceptionWhenSpecifiedContextDoesNotContainVocabulary() {
        final URI emptyCtx = Generator.generateUri();
        when(contextMapper.getVocabularyInContext(emptyCtx)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> sut.openForEditing(Set.of(emptyCtx)));
    }

    @Test
    void openForEditingClearsExistingEditedVocabularies() {
        final URI ctx = Generator.generateUri();
        final URI vocabulary = Generator.generateUri();
        when(contextMapper.getVocabularyInContext(ctx)).thenReturn(Optional.of(vocabulary));


        sut.openForEditing(Set.of(ctx));
        final InOrder inOrder = Mockito.inOrder(editableVocabularies);
        inOrder.verify(editableVocabularies).clear();
        inOrder.verify(editableVocabularies).registerEditableVocabulary(vocabulary, ctx);
    }
}
