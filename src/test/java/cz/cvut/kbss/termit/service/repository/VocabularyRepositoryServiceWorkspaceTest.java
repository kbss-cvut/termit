package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.dao.VocabularyDao;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.workspace.EditableVocabularies;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ActiveProfiles("no-cache")
@ExtendWith(MockitoExtension.class)
public class VocabularyRepositoryServiceWorkspaceTest {

    @Spy
    private final Configuration configuration = new Configuration();

    @Mock
    private EditableVocabularies editableVocabularies;

    @Mock
    private VocabularyDao dao;

    @InjectMocks
    private VocabularyRepositoryService sut;

    @Test
    void findAllAddsReadOnlyTypeToNotEditedVocabulariesWhenNotAllVocabulariesAreEditable() {
        configuration.getWorkspace().setAllVocabulariesEditable(false);
        final List<Vocabulary> vocabularies = IntStream.range(0, 5).mapToObj(i -> Generator.generateVocabularyWithId())
                                                       .collect(Collectors.toList());
        when(dao.findAll()).thenReturn(vocabularies);
        final Set<Vocabulary> readOnly = vocabularies.stream().filter(v -> Generator.randomBoolean())
                                                     .collect(Collectors.toSet());
        when(editableVocabularies.isEditable(any(Vocabulary.class))).thenReturn(true);
        readOnly.forEach(v -> when(editableVocabularies.isEditable(v)).thenReturn(false));

        final List<Vocabulary> result = sut.findAll();
        result.stream().filter(readOnly::contains)
              .forEach(v -> assertThat(v.getTypes(), hasItem(cz.cvut.kbss.termit.util.Vocabulary.s_c_pouze_pro_cteni)));
    }

    @Test
    void findAddsReadOnlyTypeToNotEditedVocabulary() {
        configuration.getWorkspace().setAllVocabulariesEditable(false);
        final Vocabulary editable = Generator.generateVocabularyWithId();
        final Vocabulary readOnly = Generator.generateVocabularyWithId();
        when(dao.find(editable.getUri())).thenReturn(Optional.of(editable));
        when(dao.find(readOnly.getUri())).thenReturn(Optional.of(readOnly));
        when(editableVocabularies.isEditable(editable)).thenReturn(true);
        when(editableVocabularies.isEditable(readOnly)).thenReturn(false);

        final Optional<Vocabulary> editableResult = sut.find(editable.getUri());
        assertTrue(editableResult.isPresent());
        assertThat(editableResult.get()
                                 .getTypes(), not(hasItem(cz.cvut.kbss.termit.util.Vocabulary.s_c_pouze_pro_cteni)));
        final Optional<Vocabulary> readOnlyResult = sut.find(readOnly.getUri());
        assertTrue(readOnlyResult.isPresent());
        assertThat(readOnlyResult.get().getTypes(), hasItem(cz.cvut.kbss.termit.util.Vocabulary.s_c_pouze_pro_cteni));
    }
}
