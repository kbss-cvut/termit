package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.termit.dto.FullTextSearchResult;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.dao.SearchDao;
import org.apache.jena.vocabulary.SKOS;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private SearchDao searchDao;

    @InjectMocks
    private SearchService sut;

    @Test
    void fullTextSearchFiltersResultsFromNonMatchingVocabularies() {
        final String searchString = "test";
        final URI vocabulary = Generator.generateUri();
        final FullTextSearchResult ftsr = new FullTextSearchResult(
                Generator.generateUri(),
                "test",
                vocabulary,
                SKOS.Concept.getURI(),
                "test",
                "test",
                1.0);
        when(searchDao.fullTextSearch(searchString)).thenReturn(Collections.singletonList(ftsr));
        final Vocabulary voc = new Vocabulary();
        voc.setUri(Generator.generateUri());
        final List<FullTextSearchResult> result = sut.fullTextSearchOfTerms(searchString,
                Collections.singleton(Generator.generateUri())
        );
        assertTrue(result.isEmpty());
        verify(searchDao).fullTextSearch(searchString);
    }

    @Test
    void fullTextSearchReturnsResultsFromMatchingVocabularies() {
        final String searchString = "test";
        final URI vocabulary = Generator.generateUri();
        final FullTextSearchResult ftsr = new FullTextSearchResult(
                Generator.generateUri(),
                "test",
                vocabulary,
                SKOS.Concept.getURI(),
                "test",
                "test",
                1.0);
        when(searchDao.fullTextSearch(searchString)).thenReturn(Collections.singletonList(ftsr));
        final List<FullTextSearchResult> result = sut.fullTextSearchOfTerms(searchString,
                Collections.singleton(vocabulary)
        );
        assertEquals(Collections.singletonList(ftsr), result);
        verify(searchDao).fullTextSearch(searchString);
    }
}
