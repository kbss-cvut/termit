package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.dto.assignment.TermAssignments;
import cz.cvut.kbss.termit.dto.listing.TermDto;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.assignment.FileOccurrenceTarget;
import cz.cvut.kbss.termit.model.assignment.TermDefinitionSource;
import cz.cvut.kbss.termit.model.comment.Comment;
import cz.cvut.kbss.termit.service.comment.CommentService;
import cz.cvut.kbss.termit.service.export.VocabularyExporters;
import cz.cvut.kbss.termit.service.export.util.TypeAwareByteArrayResource;
import cz.cvut.kbss.termit.service.repository.ChangeRecordService;
import cz.cvut.kbss.termit.service.repository.TermRepositoryService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.CsvUtils;
import cz.cvut.kbss.termit.util.TypeAwareResource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static cz.cvut.kbss.termit.environment.Generator.generateTermWithId;
import static cz.cvut.kbss.termit.environment.Generator.generateVocabulary;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TermServiceTest {

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

    @Mock
    private CommentService commentService;

    @Mock
    private Configuration configuration;

    @InjectMocks
    private TermService sut;

    private final Vocabulary vocabulary = Generator.generateVocabularyWithId();

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
        when(vocabularyService.findRequired(vocabulary.getUri())).thenReturn(vocabulary);
        final Vocabulary result = sut.findVocabularyRequired(vocabulary.getUri());
        assertEquals(vocabulary, result);
        verify(vocabularyService).findRequired(vocabulary.getUri());
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
        final List<TermDto> terms = Collections.singletonList(new TermDto(Generator.generateTermWithId()));
        when(termRepositoryService.findAllRoots(eq(vocabulary), eq(Constants.DEFAULT_PAGE_SPEC), anyCollection()))
                .thenReturn(terms);
        final List<TermDto> result = sut.findAllRoots(vocabulary, Constants.DEFAULT_PAGE_SPEC, Collections.emptyList());
        assertEquals(terms, result);
        verify(termRepositoryService).findAllRoots(vocabulary, Constants.DEFAULT_PAGE_SPEC, Collections.emptyList());
    }

    @Test
    void findAllRootsWithPagingRetrievesRootTermsUsingRepositoryService() {
        final List<TermDto> terms = Collections.singletonList(new TermDto(Generator.generateTermWithId()));
        when(termRepositoryService.findAllRoots(eq(Constants.DEFAULT_PAGE_SPEC), anyCollection()))
            .thenReturn(terms);
        final List<TermDto> result = sut.findAllRoots(Constants.DEFAULT_PAGE_SPEC, Collections.emptyList());
        assertEquals(terms, result);
        verify(termRepositoryService).findAllRoots(Constants.DEFAULT_PAGE_SPEC, Collections.emptyList());
    }

    @Test
    void findAllBySearchStringRetrievesMatchingTermsFromVocabularyUsingRepositoryService() {
        final String searchString = "test";
        final List<TermDto> terms = Collections.singletonList(new TermDto(Generator.generateTermWithId()));
        when(termRepositoryService.findAll(searchString, vocabulary)).thenReturn(terms);
        final List<TermDto> result = sut.findAll(searchString, vocabulary);
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
        final Configuration.Persistence p = new Configuration.Persistence();
        p.setLanguage("en");
        when(configuration.getPersistence()).thenReturn(p);
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
        when(termRepositoryService.existsInVocabulary(label, vocabulary, Environment.LANGUAGE)).thenReturn(true);
        assertTrue(sut.existsInVocabulary(label, vocabulary, Environment.LANGUAGE));
        verify(termRepositoryService).existsInVocabulary(label, vocabulary, Environment.LANGUAGE);
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
    void findAllCallsFindAllInRepositoryService() {
        final List<TermDto> terms = Collections.singletonList(new TermDto(Generator.generateTermWithId()));
        final String searchString = "test";
        when(termRepositoryService.findAll(searchString)).thenReturn(terms);
        final List<TermDto> result = sut.findAll(searchString);
        assertEquals(terms, result);
        verify(termRepositoryService).findAll(searchString);
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
        final List<TermDto> terms = Collections.singletonList(new TermDto(Generator.generateTermWithId()));
        when(termRepositoryService
                .findAllRootsIncludingImported(eq(vocabulary), eq(Constants.DEFAULT_PAGE_SPEC), anyCollection()))
                .thenReturn(terms);
        final List<TermDto> result = sut.findAllRootsIncludingImported(vocabulary, Constants.DEFAULT_PAGE_SPEC, Collections.emptyList());
        assertEquals(terms, result);
        verify(termRepositoryService).findAllRootsIncludingImported(vocabulary, Constants.DEFAULT_PAGE_SPEC, Collections.emptyList());
    }

    @Test
    void findAllIncludingImportedBySearchStringRetrievesMatchingTermsUsingRepositoryService() {
        final String searchString = "test";
        final List<TermDto> terms = Collections.singletonList(new TermDto(Generator.generateTermWithId()));
        when(termRepositoryService.findAllIncludingImported(searchString, vocabulary)).thenReturn(terms);
        final List<TermDto> result = sut.findAllIncludingImported(searchString, vocabulary);
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
    void setTermDefinitionReplacesExistingTermDefinition() {
        final Term term = Generator.generateTermWithId();
        final TermDefinitionSource existingSource = new TermDefinitionSource(term.getUri(),
                new FileOccurrenceTarget(Generator.generateFileWithId("existing.html")));
        term.setDefinitionSource(existingSource);
        final TermDefinitionSource definitionSource = new TermDefinitionSource();
        definitionSource.setTarget(new FileOccurrenceTarget(Generator.generateFileWithId("test.html")));
        when(termRepositoryService.findRequired(term.getUri())).thenReturn(term);

        sut.setTermDefinitionSource(term, definitionSource);
        assertEquals(term.getUri(), definitionSource.getTerm());
        verify(termOccurrenceService).removeOccurrence(existingSource);
        verify(termOccurrenceService).persistOccurrence(definitionSource);
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

    @Test
    void getCommentsRetrievesCommentsForSpecifiedTerm() {
        final Term term = Generator.generateTermWithId();
        final Comment comment = new Comment();
        comment.setAsset(term.getUri());
        comment.setCreated(new Date());
        when(commentService.findAll(term)).thenReturn(Collections.singletonList(comment));

        final List<Comment> result = sut.getComments(term);
        assertEquals(Collections.singletonList(comment), result);
        verify(commentService).findAll(term);
    }

    @Test
    void addCommentAddsCommentToTermViaCommentService() {
        final Term term = Generator.generateTermWithId();
        final Comment comment = new Comment();
        comment.setContent("test comment");
        sut.addComment(comment, term);
        verify(commentService).addToAsset(comment, term);
    }

    @Test
    void findSubTermsReturnsSubTermsSortedByLabel() {
        final Term parent = generateTermWithId();
        final Configuration.Persistence p = new Configuration.Persistence();
        p.setLanguage("en");
        when(configuration.getPersistence()).thenReturn(p);

        final List<Term> children = IntStream.range(0, 5).mapToObj(i -> {
            final Term child = generateTermWithId();
            when(termRepositoryService.find(child.getUri())).thenReturn(Optional.of(child));
            return child;
        }).collect(Collectors.toList());
        parent.setSubTerms(children.stream().map(TermInfo::new).collect(Collectors.toSet()));

        final List<Term> result = sut.findSubTerms(parent);
        children.sort(Comparator.comparing((Term t) -> t.getLabel().get(Environment.LANGUAGE)));
        assertEquals(children, result);
    }

    @Test
    void getTermCountRetrievesTermCountFromVocabularyService() {
        final Integer count = 117;
        when(vocabularyService.getTermCount(any(Vocabulary.class))).thenReturn(count);
        final Vocabulary voc = new Vocabulary();
        voc.setUri(Generator.generateUri());
        final Integer result = sut.getTermCount(voc);
        assertEquals(count, result);
        verify(vocabularyService).getTermCount(voc);
    }
}
