package cz.cvut.kbss.termit.rest.readonly;

import com.fasterxml.jackson.core.type.TypeReference;
import cz.cvut.kbss.termit.dto.readonly.ReadOnlyVocabulary;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.rest.BaseControllerTestRunner;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.readonly.ReadOnlyVocabularyService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReadOnlyVocabularyControllerTest extends BaseControllerTestRunner {

    private static final String PATH = "/public/vocabularies/";

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Configuration configuration;

    @Mock
    private ReadOnlyVocabularyService vocabularyService;

    @Mock
    private IdentifierResolver idResolver;

    @InjectMocks
    private ReadOnlyVocabularyController sut;

    @BeforeEach
    void setUp() {
        setUp(sut);
    }

    @Test
    void getAllRetrievesAllVocabulariesFromService() throws Exception {
        final List<ReadOnlyVocabulary> vocabularies = IntStream.range(0, 5).mapToObj(i -> new ReadOnlyVocabulary(
                Generator.generateVocabularyWithId())).collect(Collectors.toList());
        when(vocabularyService.findAll()).thenReturn(vocabularies);

        final MvcResult mvcResult = mockMvc.perform(get(PATH)).andExpect(status().isOk()).andReturn();
        final List<ReadOnlyVocabulary> result = readValue(mvcResult, new TypeReference<List<ReadOnlyVocabulary>>() {
        });
        assertEquals(vocabularies, result);
        verify(vocabularyService).findAll();
    }

    @Test
    void getByIdRetrievesVocabularyFromServiceAndReturnsIt() throws Exception {
        final String fragment = "test-vocabulary";
        final String namespace = Vocabulary.s_c_slovnik + "/";
        final URI uri = URI.create(namespace + fragment);
        final ReadOnlyVocabulary vocabulary = new ReadOnlyVocabulary(Generator.generateVocabularyWithId());
        vocabulary.setUri(uri);
        when(vocabularyService.findRequired(any())).thenReturn(vocabulary);
        when(idResolver.resolveIdentifier(namespace, fragment)).thenReturn(uri);

        final MvcResult mvcResult = mockMvc
                .perform(get(PATH + fragment).param(Constants.QueryParams.NAMESPACE, namespace))
                .andExpect(status().isOk()).andReturn();
        final ReadOnlyVocabulary result = readValue(mvcResult, ReadOnlyVocabulary.class);
        assertEquals(vocabulary, result);
        verify(vocabularyService).findRequired(uri);
    }

    @Test
    void getByIdReturnsNotFoundWhenServiceThrowsNotFoundException() throws Exception {
        final String fragment = "unknown";
        when(idResolver.resolveIdentifier(configuration.getNamespace().getVocabulary(), fragment))
                .thenReturn(URI.create("http://example.org/" + fragment));
        when(vocabularyService.findRequired(any())).thenThrow(NotFoundException.class);

        mockMvc.perform(get(PATH + fragment)).andExpect(status().isNotFound());
    }

    @Test
    void getTransitiveImportsRetrievesImportsFromService() throws Exception {
        final String fragment = "test-vocabulary";
        final String namespace = Vocabulary.s_c_slovnik + "/";
        final URI uri = URI.create(namespace + fragment);
        final ReadOnlyVocabulary vocabulary = new ReadOnlyVocabulary(Generator.generateVocabularyWithId());
        vocabulary.setUri(uri);
        when(vocabularyService.findRequired(any())).thenReturn(vocabulary);
        when(idResolver.resolveIdentifier(configuration.getNamespace().getVocabulary(), fragment)).thenReturn(uri);
        final Set<URI> imports = IntStream.range(0, 3).mapToObj(i -> Generator.generateUri())
                .collect(Collectors.toSet());
        when(vocabularyService.getTransitivelyImportedVocabularies(any())).thenReturn(imports);

        final MvcResult mvcResult = mockMvc.perform(get(PATH + fragment + "/imports")).andExpect(status().isOk())
                .andReturn();
        final Set<URI> result = readValue(mvcResult, new TypeReference<Set<URI>>() {
        });
        assertEquals(imports, result);
        verify(vocabularyService).getTransitivelyImportedVocabularies(vocabulary);
    }
}
