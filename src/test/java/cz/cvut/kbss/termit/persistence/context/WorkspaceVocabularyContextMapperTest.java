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
import static org.mockito.Mockito.*;

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
