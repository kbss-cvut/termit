package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.search.FullTextSearchResult;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.persistence.dao.SearchDao;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
                null,
                SKOS.CONCEPT,
                "test",
                "test",
                1.0);
        when(searchDao.fullTextSearchIncludingSnapshots(searchString)).thenReturn(Collections.singletonList(ftsr));
        final List<FullTextSearchResult> result = sut.fullTextSearchOfTerms(searchString, Collections.singleton(
                Generator.generateUri()));
        assertTrue(result.isEmpty());
        verify(searchDao).fullTextSearchIncludingSnapshots(searchString);
    }

    @Test
    void fullTextSearchReturnsResultsFromMatchingVocabularies() {
        final String searchString = "test";
        final URI vocabulary = Generator.generateUri();
        final FullTextSearchResult ftsr = new FullTextSearchResult(
                Generator.generateUri(),
                "test",
                vocabulary,
                true,
                SKOS.CONCEPT,
                "test",
                "test",
                1.0);
        when(searchDao.fullTextSearchIncludingSnapshots(searchString)).thenReturn(Collections.singletonList(ftsr));
        final List<FullTextSearchResult> result = sut.fullTextSearchOfTerms(searchString,
                                                                            Collections.singleton(vocabulary));
        assertEquals(Collections.singletonList(ftsr), result);
        verify(searchDao).fullTextSearchIncludingSnapshots(searchString);
    }
}
