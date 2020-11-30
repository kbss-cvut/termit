package cz.cvut.kbss.termit.service.business.readonly;

import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.dto.readonly.ReadOnlyTerm;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.service.business.TermService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReadOnlyTermServiceTest {

    @Mock
    private TermService termService;

    @InjectMocks
    private ReadOnlyTermService sut;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    void getRequiredVocabularyReferenceRetrievesReferenceToVocabularyFromService() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        when(termService.findVocabularyRequired(any())).thenReturn(vocabulary);

        final Vocabulary result = sut.findVocabularyRequired(vocabulary.getUri());
        assertEquals(vocabulary, result);
        verify(termService).findVocabularyRequired(vocabulary.getUri());
    }

    @Test
    void findAllRetrievesAllTermsFromServiceAndTransformsThemToReadOnlyVersion() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final List<Term> terms = Generator.generateTermsWithIds(5);
        when(termService.findAll(any(Vocabulary.class))).thenReturn(terms);

        final List<ReadOnlyTerm> result = sut.findAll(vocabulary);
        assertEquals(terms.stream().map(ReadOnlyTerm::new).collect(Collectors.toList()), result);
        verify(termService).findAll(vocabulary);
    }

    @Test
    void findAllBySearchStringSearchesForTermsViaServiceAndTransformsResultsToReadOnlyVersion() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final List<Term> terms = Generator.generateTermsWithIds(5);
        final String searchString = "test";
        when(termService.findAll(anyString(), any())).thenReturn(terms);

        final List<ReadOnlyTerm> result = sut.findAll(searchString, vocabulary);
        assertEquals(terms.stream().map(ReadOnlyTerm::new).collect(Collectors.toList()), result);
        verify(termService).findAll(searchString, vocabulary);
    }

    @Test
    void findAllIncludingImportedBySearchStringSearchesForTermsViaServiceAndTransformsResultsToReadOnlyVersion() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final List<Term> terms = Generator.generateTermsWithIds(5);
        final String searchString = "test";
        when(termService.findAllIncludingImported(anyString(), any())).thenReturn(terms);

        final List<ReadOnlyTerm> result = sut.findAllIncludingImported(searchString, vocabulary);
        assertEquals(terms.stream().map(ReadOnlyTerm::new).collect(Collectors.toList()), result);
        verify(termService).findAllIncludingImported(searchString, vocabulary);
    }

    @Test
    void findAllRootsGetsRootTermsFromServiceAndTransformsThemToReadOnlyVersion() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final List<Term> terms = Generator.generateTermsWithIds(5);
        final Pageable pageSpec = PageRequest.of(1, 10);
        when(termService.findAllRoots(any(), any(), anyCollection())).thenReturn(terms);

        final List<ReadOnlyTerm> result = sut.findAllRoots(vocabulary, pageSpec);
        assertEquals(terms.stream().map(ReadOnlyTerm::new).collect(Collectors.toList()), result);
        verify(termService).findAllRoots(vocabulary, pageSpec, Collections.emptyList());
    }

    @Test
    void findAllRootsIncludingImportedGetsRootTermsFromServiceAndTransformsThemToReadOnlyVersion() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final List<Term> terms = Generator.generateTermsWithIds(5);
        final Pageable pageSpec = PageRequest.of(1, 10);
        when(termService.findAllRootsIncludingImported(any(), any(), anyCollection())).thenReturn(terms);

        final List<ReadOnlyTerm> result = sut.findAllRootsIncludingImported(vocabulary, pageSpec);
        assertEquals(terms.stream().map(ReadOnlyTerm::new).collect(Collectors.toList()), result);
        verify(termService).findAllRootsIncludingImported(vocabulary, pageSpec, Collections.emptyList());
    }

    @Test
    void findRequiredRetrievesRequiredInstanceBySpecifiedIdentifierAndTransformsItToReadOnlyVersion() {
        final Term term = Generator.generateTermWithId();
        when(termService.findRequired(any())).thenReturn(term);

        final ReadOnlyTerm result = sut.findRequired(term.getUri());
        assertNotNull(result);
        assertEquals(new ReadOnlyTerm(term), result);
        verify(termService).findRequired(term.getUri());
    }

    @Test
    void findSubTermsRetrievesSubTermsOfSpecifiedTermFromServiceAndTransformsThemToReadOnlyVersion() {
        final Term term = Generator.generateTermWithId();
        final List<Term> subTerms = Generator.generateTermsWithIds(3);
        term.setSubTerms(subTerms.stream().map(TermInfo::new).collect(Collectors.toSet()));
        when(termService.findRequired(any())).thenReturn(term);
        when(termService.findSubTerms(any())).thenReturn(subTerms);

        final List<ReadOnlyTerm> result = sut.findSubTerms(new ReadOnlyTerm(term));
        assertEquals(subTerms.stream().map(ReadOnlyTerm::new).collect(Collectors.toList()), result);
        final ArgumentCaptor<Term> captor = ArgumentCaptor.forClass(Term.class);
        verify(termService).findSubTerms(captor.capture());
        final Term arg = captor.getValue();
        assertEquals(term.getUri(), arg.getUri());
        assertEquals(subTerms.stream().map(TermInfo::new).collect(Collectors.toSet()), arg.getSubTerms());
    }
}
