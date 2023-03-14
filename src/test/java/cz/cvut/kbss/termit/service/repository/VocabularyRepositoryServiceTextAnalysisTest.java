package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.termit.dto.listing.TermDto;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.context.VocabularyContextMapper;
import cz.cvut.kbss.termit.persistence.dao.VocabularyDao;
import cz.cvut.kbss.termit.service.business.async.AsyncTermService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static cz.cvut.kbss.termit.environment.Environment.termsToDtos;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VocabularyRepositoryServiceTextAnalysisTest {

    @Mock
    AsyncTermService termService;

    @Mock
    VocabularyContextMapper contextMapper;

    @InjectMocks
    private VocabularyRepositoryService sut;

    @Mock
    private VocabularyDao vocabularyDao;

    @Test
    void runTextAnalysisOnAllTermsInvokesTextAnalysisOnAllTermsInVocabulary() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Term termOne = Generator.generateTermWithId(vocabulary.getUri());
        final Term termTwo = Generator.generateTermWithId(vocabulary.getUri());
        List<TermDto> terms = termsToDtos(Arrays.asList(termOne, termTwo));
        when(termService.findAll(vocabulary)).thenReturn(terms);
        when(contextMapper.getVocabularyContext(vocabulary.getUri())).thenReturn(vocabulary.getUri());
        when(vocabularyDao.getTransitivelyImportedVocabularies(vocabulary)).thenReturn(Collections.emptyList());
        sut.runTextAnalysisOnAllTerms(vocabulary);
        verify(termService).asyncAnalyzeTermDefinitions(Map.of(termOne, vocabulary.getUri(),
                                                               termTwo, vocabulary.getUri()));
    }

    @Test
    void runTextAnalysisOnAllTermsInvokesTextAnalysisOnAllVocabularies() {
        final Vocabulary v = Generator.generateVocabularyWithId();
        final List<Vocabulary> vocabularies = Collections.singletonList(v);
        final Term term = Generator.generateTermWithId(v.getUri());
        when(vocabularyDao.findAll()).thenReturn(vocabularies);
        when(contextMapper.getVocabularyContext(v.getUri())).thenReturn(v.getUri());
        when(termService.findAll(v)).thenReturn(Collections.singletonList(new TermDto(term)));
        sut.runTextAnalysisOnAllVocabularies();
        verify(termService).asyncAnalyzeTermDefinitions(Map.of(term, v.getUri()));
    }
}
