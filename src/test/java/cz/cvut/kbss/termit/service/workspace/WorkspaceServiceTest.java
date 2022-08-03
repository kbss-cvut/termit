package cz.cvut.kbss.termit.service.workspace;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.persistence.context.VocabularyContextMapper;
import cz.cvut.kbss.termit.workspace.EditableVocabularies;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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

        assertThrows(NotFoundException.class, () -> sut.openForEditing(Collections.singleton(emptyCtx)));
    }
}
