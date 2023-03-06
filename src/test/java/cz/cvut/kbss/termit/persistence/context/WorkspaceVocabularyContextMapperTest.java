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
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

    @Test
    void getVocabularyContextsOverridesDelegateeWithEditableVocabularyContexts() {
        final URI vocabularyIri = Generator.generateUri();
        final Map<URI, URI> repoContexts = Map.of(Generator.generateUri(), Generator.generateUri(),
                                                  Generator.generateUri(), Generator.generateUri(),
                                                  vocabularyIri, Generator.generateUri());
        when(delegatee.getVocabularyContexts()).thenReturn(repoContexts);
        final URI contextOverride = Generator.generateUri();
        when(editableVocabularies.getRegisteredVocabularies()).thenReturn(Collections.singletonMap(vocabularyIri, contextOverride));

        final Map<URI, URI> expected = new HashMap<>(repoContexts);
        expected.put(vocabularyIri, contextOverride);
        final Map<URI, URI> result = sut.getVocabularyContexts();
        assertEquals(expected, result);
    }

    /**
     * This happens for new vocabularies that have not been published yet
     */
    @Test
    void getVocabularyContextsAddsEditableVocabularyContextsWithoutCanonicalOriginalIntoResult() {
        final Set<URI> allVocabularies = IntStream.range(0, 5).mapToObj(i -> Generator.generateUri()).collect(
                Collectors.toSet());
        final Map<URI, URI> canonicalRepoContexts = new HashMap<>();
        allVocabularies.forEach(v -> canonicalRepoContexts.put(v, Generator.generateUri()));
        final Map<URI, URI> editable = new HashMap<>();
        editable.put(allVocabularies.iterator().next(), Generator.generateUri());
        final Map<URI, URI> newVocabularies = Map.of(Generator.generateUri(), Generator.generateUri());
        editable.putAll(newVocabularies);
        when(delegatee.getVocabularyContexts()).thenReturn(canonicalRepoContexts);
        when(editableVocabularies.getRegisteredVocabularies()).thenReturn(editable);

        final Map<URI, URI> result = sut.getVocabularyContexts();
        editable.forEach((v, ctx) -> assertEquals(ctx, result.get(v)));
    }
}
