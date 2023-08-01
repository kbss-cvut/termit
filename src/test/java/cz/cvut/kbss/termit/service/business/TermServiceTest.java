package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.termit.dto.RdfsResource;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.dto.assignment.TermOccurrences;
import cz.cvut.kbss.termit.dto.listing.TermDto;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.InvalidTermStateException;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.assignment.FileOccurrenceTarget;
import cz.cvut.kbss.termit.model.assignment.TermDefinitionSource;
import cz.cvut.kbss.termit.model.comment.Comment;
import cz.cvut.kbss.termit.persistence.context.VocabularyContextMapper;
import cz.cvut.kbss.termit.service.comment.CommentService;
import cz.cvut.kbss.termit.service.document.TextAnalysisService;
import cz.cvut.kbss.termit.service.export.ExportConfig;
import cz.cvut.kbss.termit.service.export.ExportFormat;
import cz.cvut.kbss.termit.service.export.ExportType;
import cz.cvut.kbss.termit.service.export.VocabularyExporters;
import cz.cvut.kbss.termit.service.export.util.TypeAwareByteArrayResource;
import cz.cvut.kbss.termit.service.language.LanguageService;
import cz.cvut.kbss.termit.service.repository.ChangeRecordService;
import cz.cvut.kbss.termit.service.repository.TermRepositoryService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.TypeAwareResource;
import cz.cvut.kbss.termit.util.Utils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static cz.cvut.kbss.termit.environment.Generator.generateTermWithId;
import static cz.cvut.kbss.termit.environment.Generator.generateVocabulary;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyCollection;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TermServiceTest {

    @Mock
    private VocabularyExporters exporters;

    @Mock
    private VocabularyService vocabularyService;

    @Mock
    private VocabularyContextMapper vocabularyContextMapper;

    @Mock
    private TermRepositoryService termRepositoryService;

    @Mock
    private TextAnalysisService textAnalysisService;

    @Mock
    private TermOccurrenceService termOccurrenceRepositoryService;

    @Mock
    private ChangeRecordService changeRecordService;

    @Mock
    private CommentService commentService;

    @Mock
    private LanguageService languageService;

    @Spy
    private Configuration configuration = new Configuration();

    @InjectMocks
    private TermService sut;

    private final Vocabulary vocabulary = Generator.generateVocabularyWithId();

    @Test
    void exportGlossaryGetsGlossaryExportForSpecifiedVocabularyFromExporters() {
        final TypeAwareByteArrayResource resource = new TypeAwareByteArrayResource("test".getBytes(),
                ExportFormat.CSV.getMediaType(),
                ExportFormat.CSV.getFileExtension());
        final ExportConfig exportConfig = new ExportConfig(ExportType.SKOS, ExportFormat.CSV.getMediaType());
        when(exporters.exportGlossary(vocabulary, exportConfig)).thenReturn(Optional.of(resource));
        final Optional<TypeAwareResource> result = sut.exportGlossary(vocabulary, exportConfig);
        assertTrue(result.isPresent());
        assertEquals(resource, result.get());
        verify(exporters).exportGlossary(vocabulary, exportConfig);
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
    void getOccurrenceInfoRetrievesTermOccurrenceInfoFromRepositoryService() {
        final Term term = generateTermWithId();
        final List<TermOccurrences> occurrences = Collections
                .singletonList(
                        new TermOccurrences(term.getUri(), Generator.generateUri(), "test", BigInteger.valueOf(125L),
                                cz.cvut.kbss.termit.util.Vocabulary.s_c_souborovy_vyskyt_termu, true));
        when(termRepositoryService.getOccurrenceInfo(term)).thenReturn(occurrences);
        final List<TermOccurrences> result = sut.getOccurrenceInfo(term);
        assertEquals(occurrences, result);
        verify(termRepositoryService).getOccurrenceInfo(term);
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
        configuration.getPersistence().setLanguage("en");
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
        final List<TermDto> terms = Collections.singletonList(new TermDto(Generator.generateTermWithId()));
        when(termRepositoryService.findAll(vocabulary)).thenReturn(terms);
        final List<TermDto> result = sut.findAll(vocabulary);
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
        final List<TermDto> result = sut.findAllRootsIncludingImported(vocabulary, Constants.DEFAULT_PAGE_SPEC,
                Collections.emptyList());
        assertEquals(terms, result);
        verify(termRepositoryService).findAllRootsIncludingImported(vocabulary, Constants.DEFAULT_PAGE_SPEC,
                Collections.emptyList());
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
    void runTextAnalysisInvokesTextAnalysisOnSpecifiedTerm() {
        when(vocabularyContextMapper.getVocabularyContext(vocabulary.getUri())).thenReturn(vocabulary.getUri());
        final Term toAnalyze = generateTermWithId();
        sut.analyzeTermDefinition(toAnalyze, vocabulary.getUri());
        verify(textAnalysisService).analyzeTermDefinition(toAnalyze, vocabulary.getUri());
    }

    @Test
    void persistChildInvokesTextAnalysisOnPersistedChildTerm() {
        when(vocabularyContextMapper.getVocabularyContext(vocabulary.getUri())).thenReturn(vocabulary.getUri());
        final Term parent = generateTermWithId();
        parent.setVocabulary(vocabulary.getUri());
        final Term childToPersist = generateTermWithId();
        sut.persistChild(childToPersist, parent);
        verify(textAnalysisService).analyzeTermDefinition(childToPersist, parent.getVocabulary());
    }

    @Test
    void persistRootInvokesTextAnalysisOnPersistedRootTerm() {
        when(vocabularyContextMapper.getVocabularyContext(vocabulary.getUri())).thenReturn(vocabulary.getUri());
        final Term toPersist = generateTermWithId();
        sut.persistRoot(toPersist, vocabulary);
        verify(textAnalysisService).analyzeTermDefinition(toPersist, vocabulary.getUri());
    }

    @Test
    void updateInvokesTextAnalysisOnUpdatedTerm() {
        when(vocabularyContextMapper.getVocabularyContext(vocabulary.getUri())).thenReturn(vocabulary.getUri());
        final Term original = generateTermWithId(vocabulary.getUri());
        final Term toUpdate = new Term(original.getUri());
        final String newDefinition = "This term has acquired a new definition";
        toUpdate.setVocabulary(vocabulary.getUri());
        when(termRepositoryService.findRequired(toUpdate.getUri())).thenReturn(original);
        toUpdate.setDefinition(MultilingualString.create(newDefinition, Environment.LANGUAGE));
        sut.update(toUpdate);
        verify(textAnalysisService).analyzeTermDefinition(toUpdate, toUpdate.getVocabulary());
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

        sut.setTermDefinitionSource(term, definitionSource);
        assertEquals(term.getUri(), definitionSource.getTerm());
        verify(termOccurrenceRepositoryService).persist(definitionSource);
    }

    @Test
    void setTermDefinitionReplacesExistingTermDefinition() {
        final Term term = Generator.generateTermWithId();
        final TermDefinitionSource existingSource = new TermDefinitionSource(term.getUri(),
                new FileOccurrenceTarget(
                        Generator.generateFileWithId(
                                "existing.html")));
        term.setDefinitionSource(existingSource);
        final TermDefinitionSource definitionSource = new TermDefinitionSource();
        definitionSource.setTarget(new FileOccurrenceTarget(Generator.generateFileWithId("test.html")));

        sut.setTermDefinitionSource(term, definitionSource);
        assertEquals(term.getUri(), definitionSource.getTerm());
        verify(termOccurrenceRepositoryService).remove(existingSource);
        verify(termOccurrenceRepositoryService).persist(definitionSource);
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
        comment.setCreated(Utils.timestamp());
        when(commentService.findAll(eq(term), any(Instant.class), any(Instant.class))).thenReturn(
                Collections.singletonList(comment));

        final Instant from = Constants.EPOCH_TIMESTAMP;
        final Instant to = Utils.timestamp();
        final List<Comment> result = sut.getComments(term, from, to);
        assertEquals(Collections.singletonList(comment), result);
        verify(commentService).findAll(term, from, to);
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
        configuration.getPersistence().setLanguage("en");

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

    @Test
    void persistRootInvokesTextAnalysisOnAllTermsInTargetVocabulary() {
        final Term term = generateTermWithId();

        sut.persistRoot(term, vocabulary);
        final InOrder inOrder = inOrder(termRepositoryService, vocabularyService);
        inOrder.verify(termRepositoryService).addRootTermToVocabulary(term, vocabulary);
        inOrder.verify(vocabularyService).runTextAnalysisOnAllTerms(vocabulary);
    }

    @Test
    void persistChildInvokesTextAnalysisOnAllTermsInParentTermVocabulary() {
        final Term parent = generateTermWithId();
        parent.setVocabulary(vocabulary.getUri());
        final Term childToPersist = generateTermWithId();
        when(vocabularyService.getRequiredReference(vocabulary.getUri())).thenReturn(vocabulary);
        when(vocabularyContextMapper.getVocabularyContext(vocabulary.getUri())).thenReturn(vocabulary.getUri());

        sut.persistChild(childToPersist, parent);
        final InOrder inOrder = inOrder(termRepositoryService, vocabularyService);
        inOrder.verify(termRepositoryService).addChildTerm(childToPersist, parent);
        inOrder.verify(vocabularyService).runTextAnalysisOnAllTerms(vocabulary);
    }

    @Test
    void updateInvokesTextAnalysisOnAllTermsInTermsVocabularyWhenLabelHasChanged() {
        final Term original = generateTermWithId(vocabulary.getUri());
        final Term update = new Term(original.getUri());
        update.setLabel(new MultilingualString(original.getLabel().getValue()));
        update.setDefinition(new MultilingualString(original.getDefinition().getValue()));
        update.setDescription(new MultilingualString(original.getDescription().getValue()));
        update.setVocabulary(vocabulary.getUri());
        when(termRepositoryService.findRequired(original.getUri())).thenReturn(original);
        when(vocabularyService.getRequiredReference(vocabulary.getUri())).thenReturn(vocabulary);
        update.getLabel().set(Environment.LANGUAGE, "updatedLabel");

        sut.update(update);
        verify(vocabularyService).runTextAnalysisOnAllTerms(vocabulary);
    }

    @Test
    void removeTermDefinitionSourceRemovesOccurrenceRepresentingSourceOfDefinitionOfSpecifiedTerm() {
        final Term term = generateTermWithId();
        final TermDefinitionSource defSource = new TermDefinitionSource(term.getUri(),
                new FileOccurrenceTarget(
                        Generator.generateFileWithId(
                                "test.html")));
        defSource.setUri(Generator.generateUri());
        term.setDefinitionSource(defSource);

        sut.removeTermDefinitionSource(term);
        verify(termOccurrenceRepositoryService).remove(defSource);
    }

    @Test
    void removeTermDefinitionSourceDoesNothingWhenDoesNotHaveDefinitionSource() {
        final Term term = generateTermWithId();

        sut.removeTermDefinitionSource(term);
        verify(termOccurrenceRepositoryService, never()).remove(any());
    }

    @Test
    void setStateSetsStateViaRepositoryService() {
        final Term term = generateTermWithId();

        sut.setState(term, Generator.TERM_STATES[2]);
        verify(termRepositoryService).setState(term, Generator.TERM_STATES[2]);
    }

    @Test
    void setStateVerifiesThatStateExists() {
        final Term term = generateTermWithId();
        final URI state = Generator.randomItem(Generator.TERM_STATES);

        sut.setState(term, state);
        final InOrder inOrder = inOrder(languageService, termRepositoryService);
        inOrder.verify(languageService).verifyStateExists(state);
        inOrder.verify(termRepositoryService).setState(term, state);
    }

    @Test
    void setStateThrowsInvalidTermStateExceptionWhenAttemptingToSetTerminalStateToTermWhoseChildrenAreNotInTerminal() {
        final Term term = generateTermWithId();
        term.setSubTerms(Set.of(Generator.generateTermInfoWithId(), Generator.generateTermInfoWithId()));
        final List<RdfsResource> states = Stream.of(Generator.TERM_STATES)
                                                .map(uri -> new RdfsResource(uri, MultilingualString.create("State " + Generator.randomInt(0, 100), Environment.LANGUAGE), null, cz.cvut.kbss.termit.util.Vocabulary.s_c_stav_pojmu))
                                                .collect(Collectors.toList());
        final RdfsResource terminalState = states.get(states.size() - 1);
        terminalState.addType(cz.cvut.kbss.termit.util.Vocabulary.s_c_koncovy_stav_pojmu);
        assertThat(term.getSubTerms().size(), lessThan(Generator.TERM_STATES.length));
        final Iterator<TermInfo> it = term.getSubTerms().iterator();
        int i = 0;
        while (it.hasNext()) {
            it.next().setState(Generator.TERM_STATES[i++]);
        }
        when(languageService.getTermStates()).thenReturn(states);
        assertThrows(InvalidTermStateException.class, () -> sut.setState(term, terminalState.getUri()));
        verify(termRepositoryService, never()).setState(eq(term), any(URI.class));
    }

    @Test
    void setStateThrowsInvalidTermStateExceptionWhenAttemptingToSetTerminalStateToTermWhoseChildrenHaveNoState() {
        final Term term = generateTermWithId();
        term.setSubTerms(Set.of(Generator.generateTermInfoWithId(), Generator.generateTermInfoWithId()));
        final List<RdfsResource> states = Stream.of(Generator.TERM_STATES)
                                                .map(uri -> new RdfsResource(uri, MultilingualString.create("State " + Generator.randomInt(0, 100), Environment.LANGUAGE), null, cz.cvut.kbss.termit.util.Vocabulary.s_c_stav_pojmu))
                                                .collect(Collectors.toList());
        final RdfsResource terminalState = states.get(states.size() - 1);
        terminalState.addType(cz.cvut.kbss.termit.util.Vocabulary.s_c_koncovy_stav_pojmu);
        when(languageService.getTermStates()).thenReturn(states);
        assertThrows(InvalidTermStateException.class, () -> sut.setState(term, terminalState.getUri()));
        verify(termRepositoryService, never()).setState(eq(term), any(URI.class));
    }

    @Test
    void findConsolidatesTermRelationships() {
        final Term term = spy(generateTermWithId());
        when(termRepositoryService.find(term.getUri())).thenReturn(Optional.of(term));

        final Optional<Term> result = sut.find(term.getUri());
        assertTrue(result.isPresent());
        assertEquals(term, result.get());
        verify(term).consolidateInferred();
        verify(term).consolidateParents();
    }

    @Test
    void findConsolidatesParentTerms() {
        final Term term = spy(generateTermWithId());
        when(termRepositoryService.find(term.getUri())).thenReturn(Optional.of(term));

        final Optional<Term> result = sut.find(term.getUri());
        assertTrue(result.isPresent());
        assertEquals(term, result.get());
    }

    @Test
    void findRequiredConsolidatesTermRelationships() {
        final Term term = spy(generateTermWithId());
        when(termRepositoryService.findRequired(term.getUri())).thenReturn(term);

        final Term result = sut.findRequired(term.getUri());
        assertEquals(term, result);
        verify(term).consolidateInferred();
        verify(term).consolidateParents();
    }

    @Test
    void findSnapshotsRetrievesTermSnapshotsFromRepositoryService() {
        final Term term = generateTermWithId();

        sut.findSnapshots(term);
        verify(termRepositoryService).findSnapshots(term);
    }

    @Test
    void findVersionValidAtRetrievesValidVersionFromRepositoryService() {
        final Term term = generateTermWithId();
        final Instant instant = Instant.now();
        when(termRepositoryService.findVersionValidAt(term, instant)).thenReturn(Optional.of(term));

        sut.findVersionValidAt(term, instant);
        verify(termRepositoryService).findVersionValidAt(term, instant);
    }

    @Test
    void findVersionValidAtThrowsNotFoundExceptionWhenNoValidVersionExists() {
        final Term term = generateTermWithId();
        final Instant instant = Instant.now();
        when(termRepositoryService.findVersionValidAt(term, instant)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> sut.findVersionValidAt(term, instant));
    }

    @Test
    void findVersionValidAtConsolidatesAttributeOnLoadedInstance() {
        final Term term = generateTermWithId();
        final Instant instant = Instant.now();
        final TermInfo related = Generator.generateTermInfoWithId();
        final TermInfo inverseRelated = Generator.generateTermInfoWithId();
        term.addRelatedTerm((related));
        term.setInverseRelated(Collections.singleton(inverseRelated));
        when(termRepositoryService.findVersionValidAt(term, instant)).thenReturn(Optional.of(term));

        final Term result = sut.findVersionValidAt(term, instant);
        assertThat(result.getRelated(), hasItems(related, inverseRelated));
    }

    @Test
    void updateVerifiesThatStateExistsTermState() {
        final Term original = generateTermWithId(vocabulary.getUri());
        when(termRepositoryService.findRequired(original.getUri())).thenReturn(original);
        final Term update = new Term(original.getUri());
        update.setLabel(new MultilingualString(original.getLabel().getValue()));
        update.setDefinition(new MultilingualString(original.getDefinition().getValue()));
        update.setDescription(new MultilingualString(original.getDescription().getValue()));
        update.setVocabulary(vocabulary.getUri());
        update.setState(Generator.randomItem(Generator.TERM_STATES));
        sut.update(update);
        final InOrder inOrder = inOrder(languageService, termRepositoryService);
        inOrder.verify(languageService).verifyStateExists(update.getState());
        inOrder.verify(termRepositoryService).update(update);
    }

    @Test
    void persistRootSetsInitialStateOfPersistedInstance() {
        final Term toPersist = Generator.generateTerm();
        final RdfsResource initialState = new RdfsResource(Generator.TERM_STATES[0], MultilingualString.create("Initial", Environment.LANGUAGE), null, cz.cvut.kbss.termit.util.Vocabulary.s_c_uvodni_stav_pojmu);
        when(languageService.getInitialTermState()).thenReturn(Optional.of(initialState));

        sut.persistRoot(toPersist, vocabulary);
        verify(languageService).getInitialTermState();
        assertEquals(Generator.TERM_STATES[0], toPersist.getState());
    }

    @Test
    void persistChildSetsInitialStateOfPersistedInstance() {
        final Term parent = Generator.generateTermWithId(vocabulary.getUri());
        final Term toPersist = Generator.generateTerm();
        final RdfsResource initialState = new RdfsResource(Generator.TERM_STATES[0], MultilingualString.create("Initial", Environment.LANGUAGE), null, cz.cvut.kbss.termit.util.Vocabulary.s_c_uvodni_stav_pojmu);
        when(languageService.getInitialTermState()).thenReturn(Optional.of(initialState));

        sut.persistChild(toPersist, parent);
        verify(languageService).getInitialTermState();
        assertEquals(Generator.TERM_STATES[0], toPersist.getState());
    }
}
