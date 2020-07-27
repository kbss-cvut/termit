package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.dto.assignment.TermAssignments;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.exception.TermDefinitionSourceExistsException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.assignment.FileOccurrenceTarget;
import cz.cvut.kbss.termit.model.assignment.TermDefinitionSource;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import cz.cvut.kbss.termit.service.export.VocabularyExporters;
import cz.cvut.kbss.termit.service.export.util.TypeAwareByteArrayResource;
import cz.cvut.kbss.termit.service.repository.ChangeRecordService;
import cz.cvut.kbss.termit.service.repository.TermRepositoryService;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.CsvUtils;
import cz.cvut.kbss.termit.util.TypeAwareResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static cz.cvut.kbss.termit.environment.Generator.generateTermWithId;
import static cz.cvut.kbss.termit.environment.Generator.generateVocabulary;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TermServiceTest extends BaseServiceTestRunner {

    @Mock
    private VocabularyExporters exporters;

    @Mock
    private VocabularyService vocabularyService;

    @Mock
    private TermRepositoryService termRepositoryService;

    @Mock
    private TermOccurrenceService termOccurrenceService;

    @Mock
    private ChangeRecordService changeRecordService;

    @InjectMocks
    private TermService sut;

    private Vocabulary vocabulary;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        this.vocabulary = Generator.generateVocabulary();
        vocabulary.setUri(Generator.generateUri());
    }

    @Test
    void exportGlossaryGetsGlossaryExportForSpecifiedVocabularyFromExporters() {
        final TypeAwareByteArrayResource resource = new TypeAwareByteArrayResource("test".getBytes(),
                CsvUtils.MEDIA_TYPE, CsvUtils.FILE_EXTENSION);
        when(exporters.exportVocabularyGlossary(vocabulary, CsvUtils.MEDIA_TYPE)).thenReturn(Optional.of(resource));
        final Optional<TypeAwareResource> result = sut.exportGlossary(vocabulary, CsvUtils.MEDIA_TYPE);
        assertTrue(result.isPresent());
        assertEquals(resource, result.get());
        verify(exporters).exportVocabularyGlossary(vocabulary, CsvUtils.MEDIA_TYPE);
    }

    @Test
    void findVocabularyLoadsVocabularyFromRepositoryService() {
        when(vocabularyService.find(vocabulary.getUri())).thenReturn(Optional.of(vocabulary));
        final Vocabulary result = sut.findVocabularyRequired(vocabulary.getUri());
        assertEquals(vocabulary, result);
        verify(vocabularyService).find(vocabulary.getUri());
    }

    @Test
    void findVocabularyThrowsNotFoundExceptionWhenVocabularyIsNotFound() {
        when(vocabularyService.find(any())).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> sut.findVocabularyRequired(vocabulary.getUri()));
    }

    @Test
    void findFindsTermByIdInRepositoryService() {
        final Term t = Generator.generateTermWithId();
        when(termRepositoryService.find(t.getUri())).thenReturn(Optional.of(t));
        final Optional<Term> result = sut.find(t.getUri());
        assertTrue(result.isPresent());
        assertEquals(t, result.get());
        verify(termRepositoryService).find(t.getUri());
    }

    @Test
    void findAllRootsWithPagingRetrievesRootTermsFromVocabularyUsingRepositoryService() {
        final List<Term> terms = Collections.singletonList(Generator.generateTermWithId());
        when(termRepositoryService.findAllRoots(eq(vocabulary), eq(Constants.DEFAULT_PAGE_SPEC), anyCollection()))
                .thenReturn(terms);
        final List<Term> result = sut.findAllRoots(vocabulary, Constants.DEFAULT_PAGE_SPEC, Collections.emptyList());
        assertEquals(terms, result);
        verify(termRepositoryService).findAllRoots(vocabulary, Constants.DEFAULT_PAGE_SPEC, Collections.emptyList());
    }

    @Test
    void findAllBySearchStringRetrievesMatchingTermsFromVocabularyUsingRepositoryService() {
        final String searchString = "test";
        final List<Term> terms = Collections.singletonList(Generator.generateTermWithId());
        when(termRepositoryService.findAll(searchString, vocabulary)).thenReturn(terms);
        final List<Term> result = sut.findAll(searchString, vocabulary);
        assertEquals(terms, result);
        verify(termRepositoryService).findAll(searchString, vocabulary);
    }

    @Test
    void getAssignmentInfoRetrievesTermAssignmentInfoFromRepositoryService() {
        final Term term = generateTermWithId();
        final List<TermAssignments> assignments = Collections
                .singletonList(new TermAssignments(term.getUri(), Generator.generateUri(), "test", true));
        when(termRepositoryService.getAssignmentsInfo(term)).thenReturn(assignments);
        final List<TermAssignments> result = sut.getAssignmentInfo(term);
        assertEquals(assignments, result);
        verify(termRepositoryService).getAssignmentsInfo(term);
    }

    @Test
    void persistUsesRepositoryServiceToPersistTermIntoVocabulary() {
        final Term term = generateTermWithId();
        sut.persistRoot(term, vocabulary);
        verify(termRepositoryService).addRootTermToVocabulary(term, vocabulary);
    }

    @Test
    void persistUsesRepositoryServiceToPersistTermAsChildOfSpecifiedParentTerm() {
        final Term parent = generateTermWithId();
        final Term toPersist = generateTermWithId();
        sut.persistChild(toPersist, parent);
        verify(termRepositoryService).addChildTerm(toPersist, parent);
    }

    @Test
    void updateUsesRepositoryServiceToUpdateTerm() {
        final Term term = generateTermWithId();
        when(termRepositoryService.findRequired(term.getUri())).thenReturn(term);
        sut.update(term);
        verify(termRepositoryService).update(term);
    }

    @Test
    void findSubTermsReturnsEmptyCollectionForTermWithoutSubTerms() {
        final Term term = generateTermWithId();
        final List<Term> result = sut.findSubTerms(term);
        assertTrue(result.isEmpty());
    }

    @Test
    void findSubTermsLoadsChildTermsOfTermUsingRepositoryService() {
        final Term parent = generateTermWithId();
        final List<Term> children = IntStream.range(0, 5).mapToObj(i -> {
            final Term child = generateTermWithId();
            when(termRepositoryService.find(child.getUri())).thenReturn(Optional.of(child));
            return child;
        }).collect(Collectors.toList());
        parent.setSubTerms(children.stream().map(TermInfo::new).collect(Collectors.toSet()));

        final List<Term> result = sut.findSubTerms(parent);
        assertEquals(children.size(), result.size());
        assertTrue(children.containsAll(result));
    }

    @Test
    void existsInVocabularyChecksForLabelExistenceInVocabularyViaRepositoryService() {
        final String label = "test";
        when(termRepositoryService.existsInVocabulary(label, vocabulary)).thenReturn(true);
        assertTrue(sut.existsInVocabulary(label, vocabulary));
        verify(termRepositoryService).existsInVocabulary(label, vocabulary);
    }

    @Test
    void findAllRetrievesAllTermsFromVocabularyUsingRepositoryService() {
        final List<Term> terms = Collections.singletonList(Generator.generateTermWithId());
        when(termRepositoryService.findAll(vocabulary)).thenReturn(terms);
        final List<Term> result = sut.findAll(vocabulary);
        assertEquals(terms, result);
        verify(termRepositoryService).findAll(vocabulary);
    }

    @Test
    void getReferenceRetrievesTermReferenceFromRepositoryService() {
        final Term t = Generator.generateTermWithId();
        when(termRepositoryService.getReference(t.getUri())).thenReturn(Optional.of(t));
        final Optional<Term> result = sut.getReference(t.getUri());
        assertTrue(result.isPresent());
        assertEquals(t, result.get());
        verify(termRepositoryService).getReference(t.getUri());
    }

    @Test
    void getRequiredReferenceRetrievesTermReferenceFromRepositoryService() {
        final Term t = Generator.generateTermWithId();
        when(termRepositoryService.getRequiredReference(t.getUri())).thenReturn(t);
        final Term result = sut.getRequiredReference(t.getUri());
        assertEquals(t, result);
        verify(termRepositoryService).getRequiredReference(t.getUri());
    }

    @Test
    void findAllRootsIncludingImportsRetrievesRootTermsUsingRepositoryService() {
        final List<Term> terms = Collections.singletonList(Generator.generateTermWithId());
        when(termRepositoryService
                .findAllRootsIncludingImported(eq(vocabulary), eq(Constants.DEFAULT_PAGE_SPEC), anyCollection()))
                .thenReturn(terms);
        final List<Term> result = sut
                .findAllRootsIncludingImported(vocabulary, Constants.DEFAULT_PAGE_SPEC, Collections.emptyList());
        assertEquals(terms, result);
        verify(termRepositoryService)
                .findAllRootsIncludingImported(vocabulary, Constants.DEFAULT_PAGE_SPEC, Collections.emptyList());
    }

    @Test
    void findAllIncludingImportedBySearchStringRetrievesMatchingTermsUsingRepositoryService() {
        final String searchString = "test";
        final List<Term> terms = Collections.singletonList(Generator.generateTermWithId());
        when(termRepositoryService.findAllIncludingImported(searchString, vocabulary)).thenReturn(terms);
        final List<Term> result = sut.findAllIncludingImported(searchString, vocabulary);
        assertEquals(terms, result);
        verify(termRepositoryService).findAllIncludingImported(searchString, vocabulary);
    }

    @Test
    void removeRemovesTermViaRepositoryService() {
        final Term toRemove = generateTermWithId();
        sut.remove(toRemove);
        verify(termRepositoryService).remove(toRemove);
    }

    @Test
    void getUnusedTermsReturnsUnusedTermsInVocabulary() {
        final List<URI> terms = Collections.singletonList(Generator.generateUri());
        final Vocabulary vocabulary = generateVocabulary();
        when(termRepositoryService.getUnusedTermsInVocabulary(vocabulary)).thenReturn(terms);
        final List<URI> result = sut.getUnusedTermsInVocabulary(vocabulary);
        assertEquals(terms, result);
        verify(termRepositoryService).getUnusedTermsInVocabulary(vocabulary);
    }

    @Test
    void setTermDefinitionSourceSetsTermOnDefinitionAndPersistsIt() {
        final Term term = Generator.generateTermWithId();
        final TermDefinitionSource definitionSource = new TermDefinitionSource();
        definitionSource.setTarget(new FileOccurrenceTarget(Generator.generateFileWithId("test.html")));
        when(termRepositoryService.findRequired(term.getUri())).thenReturn(term);

        sut.setTermDefinitionSource(term, definitionSource);
        assertEquals(term.getUri(), definitionSource.getTerm());
        verify(termOccurrenceService).persistOccurrence(definitionSource);
    }

    @Test
    void setTermDefinitionThrowsTermDefinitionSourceExistsExceptionWhenTermAlreadyHasDefinition() {
        final Term term = Generator.generateTermWithId();
        final TermDefinitionSource existingSource = new TermDefinitionSource(term.getUri(),
                new FileOccurrenceTarget(Generator.generateFileWithId("existing.html")));
        term.setDefinitionSource(existingSource);
        final TermDefinitionSource definitionSource = new TermDefinitionSource();
        definitionSource.setTarget(new FileOccurrenceTarget(Generator.generateFileWithId("test.html")));
        when(termRepositoryService.findRequired(term.getUri())).thenReturn(term);

        assertThrows(TermDefinitionSourceExistsException.class,
                () -> sut.setTermDefinitionSource(term, definitionSource));
        verify(termOccurrenceService, never()).persistOccurrence(definitionSource);
    }

    @Test
    void getChangesRetrievesChangeRecordsFromChangeRecordService() {
        final Term asset = Generator.generateTermWithId();
        sut.getChanges(asset);
        verify(changeRecordService).getChanges(asset);
    }

    @Test
    void findAllIncludingImportedRetrievesAllTermsFromVocabularyImportsChain() {
        sut.findAllIncludingImported(vocabulary);
        verify(termRepositoryService).findAllIncludingImported(vocabulary);
    }
}
