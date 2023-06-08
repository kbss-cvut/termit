package cz.cvut.kbss.termit.service.security.authorization;

import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.search.FullTextSearchResult;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Vocabulary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchAuthorizationServiceTest {

    @Mock
    private VocabularyAuthorizationService vocabularyAuthorizationService;

    @InjectMocks
    private SearchAuthorizationService sut;

    @Test
    void canReadChecksIfVocabularyIsReadableForTermResult() {
        final FullTextSearchResult res = new FullTextSearchResult(Generator.generateUri(), "test string",
                                                                  Generator.generateUri(), false,
                                                                  SKOS.CONCEPT, "label", "test",
                                                                  (double) Generator.randomInt());
        when(vocabularyAuthorizationService.canRead(any(Vocabulary.class))).thenReturn(true);
        assertTrue(sut.canRead(res));
        verify(vocabularyAuthorizationService).canRead(new Vocabulary(res.getVocabulary()));
    }

    @Test
    void canReadChecksIfVocabularyIsReadableForVocabularyResult() {
        final FullTextSearchResult res = new FullTextSearchResult(Generator.generateUri(), "test label",
                                                                  null, false,
                                                                  cz.cvut.kbss.termit.util.Vocabulary.s_c_slovnik,
                                                                  "label", "test",
                                                                  (double) Generator.randomInt());
        assertFalse(sut.canRead(res));
        verify(vocabularyAuthorizationService).canRead(new Vocabulary(res.getUri()));
    }
}
