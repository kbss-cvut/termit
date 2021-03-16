package cz.cvut.kbss.termit.service.business.readonly;

import cz.cvut.kbss.termit.dto.readonly.ReadOnlyVocabulary;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.service.business.VocabularyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReadOnlyVocabularyServiceTest {

    @Mock
    private VocabularyService vocabularyService;

    @InjectMocks
    private ReadOnlyVocabularyService sut;

    @Test
    void findAllReturnsAllVocabulariesTransformedToReadOnlyVersions() {
        final List<Vocabulary> vocabularies = IntStream.range(0, 5).mapToObj(i -> Generator.generateVocabularyWithId())
                .collect(Collectors.toList());
        when(vocabularyService.findAll()).thenReturn(vocabularies);

        final List<ReadOnlyVocabulary> result = sut.findAll();
        assertEquals(vocabularies.size(), result.size());
        result.forEach(r -> assertTrue(vocabularies.stream().anyMatch(v -> v.getUri().equals(r.getUri()))));
    }

    @Test
    void findRequiredRetrievesReadOnlyVersionOfVocabularyWithSpecifiedId() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        when(vocabularyService.findRequired(vocabulary.getUri())).thenReturn(vocabulary);

        final ReadOnlyVocabulary result = sut.findRequired(vocabulary.getUri());
        assertNotNull(result);
        assertEquals(vocabulary.getUri(), result.getUri());
    }

    @Test
    void findRequiredThrowsNotFoundExceptionWhenNoMatchingVocabularyIsFound() {
        when(vocabularyService.findRequired(any())).thenThrow(NotFoundException.class);
        assertThrows(NotFoundException.class, () -> sut.findRequired(Generator.generateUri()));
    }

    @Test
    void getTransitivelyImportedVocabulariesRetrievesImportedVocabulariesFromVocabularyService() {
        final ReadOnlyVocabulary voc = new ReadOnlyVocabulary(Generator.generateVocabularyWithId());
        final Set<URI> imports = IntStream.range(0, 3).mapToObj(i -> Generator.generateUri()).collect(Collectors.toSet());
        when(vocabularyService.getTransitivelyImportedVocabularies(any())).thenReturn(imports);

        final Collection<URI> result = sut.getTransitivelyImportedVocabularies(voc);
        assertEquals(imports, result);
        final ArgumentCaptor<Vocabulary> captor = ArgumentCaptor.forClass(Vocabulary.class);
        verify(vocabularyService).getTransitivelyImportedVocabularies(captor.capture());
        assertEquals(voc.getUri(), captor.getValue().getUri());
    }
}
