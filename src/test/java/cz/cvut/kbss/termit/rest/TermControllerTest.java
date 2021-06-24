package cz.cvut.kbss.termit.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.jsonldjava.utils.JsonUtils;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.jsonld.JsonLd;
import cz.cvut.kbss.termit.dto.assignment.TermAssignments;
import cz.cvut.kbss.termit.dto.listing.TermDto;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.assignment.FileOccurrenceTarget;
import cz.cvut.kbss.termit.model.assignment.TermDefinitionSource;
import cz.cvut.kbss.termit.model.changetracking.AbstractChangeRecord;
import cz.cvut.kbss.termit.model.changetracking.UpdateChangeRecord;
import cz.cvut.kbss.termit.model.comment.Comment;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.rest.handler.ErrorInfo;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.TermService;
import cz.cvut.kbss.termit.service.export.util.TypeAwareByteArrayResource;
import cz.cvut.kbss.termit.util.*;
import cz.cvut.kbss.termit.util.Constants.Excel;
import cz.cvut.kbss.termit.util.Constants.QueryParams;
import cz.cvut.kbss.termit.util.Constants.Turtle;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static cz.cvut.kbss.termit.environment.Environment.termsToDtos;
import static cz.cvut.kbss.termit.util.Constants.DEFAULT_PAGE_SPEC;
import static cz.cvut.kbss.termit.util.Constants.QueryParams.PAGE;
import static cz.cvut.kbss.termit.util.Constants.QueryParams.PAGE_SIZE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class TermControllerTest extends BaseControllerTestRunner {

    private static final String PATH = "/vocabularies/";
    private static final String VOCABULARY_NAME = "metropolitan-plan";
    private static final String TERM_NAME = "locality";
    private static final String VOCABULARY_URI = Environment.BASE_URI + "/" + VOCABULARY_NAME;
    private static final String NAMESPACE = VOCABULARY_URI + "/pojem/";
    private static final String TERM_URI = NAMESPACE + TERM_NAME;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Configuration config;

    @Mock
    private IdentifierResolver idResolverMock;

    @Mock
    private TermService termServiceMock;

    @InjectMocks
    private TermController sut;

    private cz.cvut.kbss.termit.model.Vocabulary vocabulary;

    @BeforeEach
    void setUp() {
        super.setUp(sut);
        this.vocabulary = Generator.generateVocabulary();
        vocabulary.setLabel(VOCABULARY_NAME);
        vocabulary.setUri(URI.create(VOCABULARY_URI));
    }

    @Test
    void termsExistCheckReturnOkIfTermLabelExistsInVocabulary() throws Exception {
        final String name = "test term";
        final String namespace = "http://onto.fel.cvut.cz/ontologies/termit/vocabularies/";
        final String language = "en";
        final URI vocabularyUri = URI.create(namespace + VOCABULARY_NAME);
        when(idResolverMock.resolveIdentifier(namespace, VOCABULARY_NAME)).thenReturn(vocabularyUri);
        when(termServiceMock.getRequiredVocabularyReference(vocabularyUri)).thenReturn(vocabulary);
        when(termServiceMock.existsInVocabulary(any(), any(), any())).thenReturn(true);
        mockMvc.perform(
                head(PATH + VOCABULARY_NAME + "/terms")
                        .param(QueryParams.NAMESPACE, namespace)
                        .param("prefLabel", name)
                        .param("language", language))
               .andExpect(status().isOk()).andReturn();
        verify(termServiceMock).existsInVocabulary(name, vocabulary, language);
    }

    @Test
    void termsExistCheckReturn404IfTermLabelDoesNotExistInVocabulary() throws Exception {
        final String name = "test term";
        final String namespace = "http://onto.fel.cvut.cz/ontologies/termit/vocabularies/";
        final String language = "en";
        final URI vocabularyUri = URI.create(namespace + VOCABULARY_NAME);
        when(idResolverMock.resolveIdentifier(namespace, VOCABULARY_NAME)).thenReturn(vocabularyUri);
        when(termServiceMock.getRequiredVocabularyReference(vocabularyUri)).thenReturn(vocabulary);
        when(termServiceMock.existsInVocabulary(any(), any(), any())).thenReturn(false);
        mockMvc.perform(
                head(PATH + VOCABULARY_NAME + "/terms")
                        .param(QueryParams.NAMESPACE, namespace)
                        .param("prefLabel", name)
                        .param("language", language))
               .andExpect(status().is4xxClientError()).andReturn();
        verify(termServiceMock).existsInVocabulary(name, vocabulary, language);
    }

    @Test
    void getByIdResolvesTermFullIdentifierAndLoadsTermFromService() throws Exception {
        final URI termUri = initTermUriResolution();
        final Term term = Generator.generateTerm();
        term.setUri(termUri);
        when(termServiceMock.findRequired(termUri)).thenReturn(term);
        final MvcResult mvcResult = mockMvc.perform(get(PATH + VOCABULARY_NAME + "/terms/" + TERM_NAME))
                                           .andExpect(status().isOk()).andReturn();
        final Term result = readValue(mvcResult, Term.class);
        assertEquals(term, result);
    }

    @Test
    void updateUpdatesTerm() throws Exception {
        final URI termUri = initTermUriResolution();
        final Term term = Generator.generateTerm();
        term.setUri(termUri);
        mockMvc.perform(put(PATH + VOCABULARY_NAME + "/terms/" + TERM_NAME).content(toJson(term)).contentType(
                MediaType.APPLICATION_JSON_VALUE)).andExpect(status().isNoContent());
        verify(termServiceMock).update(term);
    }

    @Test
    void updateThrowsValidationExceptionWhenTermUriDoesNotMatchRequestPath() throws Exception {
        initTermUriResolution();
        final Term term = Generator.generateTermWithId();
        final MvcResult mvcResult = mockMvc
                .perform(put(PATH + VOCABULARY_NAME + "/terms/" + TERM_NAME).content(toJson(term)).contentType(
                        MediaType.APPLICATION_JSON_VALUE)).andExpect(status().isConflict()).andReturn();
        final ErrorInfo errorInfo = readValue(mvcResult, ErrorInfo.class);
        assertThat(errorInfo.getMessage(), containsString("does not match the ID of the specified entity"));
        verify(termServiceMock, never()).update(any());
    }

    private URI initTermUriResolution() {
        final URI termUri = URI.create(Environment.BASE_URI + "/" + VOCABULARY_NAME +
                config.getNamespace().getTerm().getSeparator() + "/" + TERM_NAME);
        when(idResolverMock.resolveIdentifier(config.getNamespace().getVocabulary(), VOCABULARY_NAME))
                .thenReturn(URI.create(VOCABULARY_URI));
        when(idResolverMock.buildNamespace(eq(VOCABULARY_URI), any())).thenReturn(NAMESPACE);
        when(idResolverMock.resolveIdentifier(NAMESPACE, TERM_NAME)).thenReturn(termUri);
        return termUri;
    }

    @Test
    void getSubTermsLoadsSubTermsOfParentTerm() throws Exception {
        final URI termUri = initTermUriResolution();
        final Term term = Generator.generateTerm();
        term.setUri(termUri);
        when(termServiceMock.findRequired(term.getUri())).thenReturn(term);
        final List<Term> children = Generator.generateTermsWithIds(3);
        when(termServiceMock.findSubTerms(term)).thenReturn(children);

        final MvcResult mvcResult = mockMvc
                .perform(get(PATH + VOCABULARY_NAME + "/terms/" + TERM_NAME + "/subterms"))
                .andExpect(status().isOk()).andReturn();
        final List<Term> result = readValue(mvcResult, new TypeReference<List<Term>>() {
        });
        assertEquals(children.size(), result.size());
        assertTrue(children.containsAll(result));
    }

    @Test
    void getSubTermsReturnsEmptyListForTermWithoutSubTerms() throws Exception {
        final URI termUri = initTermUriResolution();
        final Term term = Generator.generateTerm();
        term.setUri(termUri);
        when(termServiceMock.findRequired(termUri)).thenReturn(term);
        final MvcResult mvcResult = mockMvc
                .perform(get(PATH + VOCABULARY_NAME + "/terms/" + TERM_NAME + "/subterms"))
                .andExpect(status().isOk()).andReturn();
        final List<Term> result = readValue(mvcResult, new TypeReference<List<Term>>() {
        });
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getTermReturnsTermWithUnmappedProperties() throws Exception {
        final URI termUri = initTermUriResolution();
        final Term term = Generator.generateTerm();
        final String customProperty = Vocabulary.s_p_has_dataset;
        final String value = "Test";
        term.setProperties(Collections.singletonMap(customProperty, Collections.singleton(value)));
        term.setUri(termUri);
        when(termServiceMock.findRequired(termUri)).thenReturn(term);
        final MvcResult mvcResult = mockMvc.perform(get(PATH + VOCABULARY_NAME + "/terms/" + TERM_NAME).accept(
                JsonLd.MEDIA_TYPE)).andReturn();
        final Map jsonObj = (Map) JsonUtils.fromString(mvcResult.getResponse().getContentAsString());
        assertTrue(jsonObj.containsKey(customProperty));
        assertEquals(value, jsonObj.get(customProperty));
    }

    @Test
    void getAllReturnsAllTermsFromVocabulary() throws Exception {
        when(idResolverMock.resolveIdentifier(Environment.BASE_URI, VOCABULARY_NAME))
                .thenReturn(URI.create(VOCABULARY_URI));
        final List<Term> terms = Generator.generateTermsWithIds(5);
        when(termServiceMock.findVocabularyRequired(vocabulary.getUri())).thenReturn(vocabulary);
        when(termServiceMock.findAll(eq(vocabulary))).thenReturn(terms);

        final MvcResult mvcResult = mockMvc.perform(
                get(PATH + VOCABULARY_NAME + "/terms")
                        .param(QueryParams.NAMESPACE, Environment.BASE_URI))
                                           .andExpect(status().isOk()).andReturn();
        final List<Term> result = readValue(mvcResult, new TypeReference<List<Term>>() {
        });
        assertEquals(terms, result);
        verify(termServiceMock).findAll(vocabulary);
    }

    @Test
    void getAllReturnsAllTermsFromVocabularyChainWhenIncludeImportedIsSpecified() throws Exception {
        when(idResolverMock.resolveIdentifier(Environment.BASE_URI, VOCABULARY_NAME))
                .thenReturn(URI.create(VOCABULARY_URI));
        final List<TermDto> terms = termsToDtos(Generator.generateTermsWithIds(5));
        when(termServiceMock.findVocabularyRequired(vocabulary.getUri())).thenReturn(vocabulary);
        when(termServiceMock.findAllIncludingImported(eq(vocabulary))).thenReturn(terms);

        final MvcResult mvcResult = mockMvc.perform(
                get(PATH + VOCABULARY_NAME + "/terms")
                        .param(QueryParams.NAMESPACE, Environment.BASE_URI)
                        .param("includeImported", Boolean.TRUE.toString()))
                                           .andExpect(status().isOk()).andReturn();
        final List<TermDto> result = readValue(mvcResult, new TypeReference<List<TermDto>>() {
        });
        assertEquals(terms, result);
        verify(termServiceMock).findAllIncludingImported(vocabulary);
    }

    @Test
    void getAllUsesSearchStringToFindMatchingTerms() throws Exception {
        when(idResolverMock.resolveIdentifier(Environment.BASE_URI, VOCABULARY_NAME))
                .thenReturn(URI.create(VOCABULARY_URI));
        when(termServiceMock.findVocabularyRequired(vocabulary.getUri())).thenReturn(vocabulary);
        final List<TermDto> terms = termsToDtos(Generator.generateTermsWithIds(5));
        when(termServiceMock.findAll(anyString(), any())).thenReturn(terms);
        final String searchString = "test";

        final MvcResult mvcResult = mockMvc.perform(get(PATH + VOCABULARY_NAME + "/terms")
                .param(QueryParams.NAMESPACE, Environment.BASE_URI)
                .param("searchString", searchString)).andExpect(status().isOk()).andReturn();
        final List<TermDto> result = readValue(mvcResult, new TypeReference<List<TermDto>>() {
        });
        assertEquals(terms, result);
        verify(termServiceMock).findAll(searchString, vocabulary);
    }

    @Test
    void getSubTermsFindsSubTermsOfTermWithSpecifiedId() throws Exception {
        when(idResolverMock.resolveIdentifier(config.getNamespace().getVocabulary(), VOCABULARY_NAME))
                .thenReturn(URI.create(VOCABULARY_URI));
        final Term parent = Generator.generateTermWithId();
        when(idResolverMock.buildNamespace(VOCABULARY_URI, config.getNamespace().getTerm().getSeparator()))
                .thenReturn(VOCABULARY_URI);
        when(idResolverMock.resolveIdentifier(VOCABULARY_URI, parent.getLabel().get())).thenReturn(parent.getUri());
        when(termServiceMock.findRequired(parent.getUri())).thenReturn(parent);
        final List<Term> children = Generator.generateTermsWithIds(5);
        when(termServiceMock.findSubTerms(parent)).thenReturn(children);

        final MvcResult mvcResult = mockMvc
                .perform(get(PATH + VOCABULARY_NAME + "/terms/" + parent.getLabel().get(Environment.LANGUAGE) +
                        "/subterms"))
                .andExpect(status().isOk()).andReturn();
        final List<Term> result = readValue(mvcResult, new TypeReference<List<Term>>() {
        });
        assertEquals(children.size(), result.size());
        assertTrue(children.containsAll(result));
        verify(termServiceMock).findRequired(parent.getUri());
        verify(termServiceMock).findSubTerms(parent);
    }

    @Test
    void getAssignmentInfoGetsTermAssignmentInfoFromService() throws Exception {
        final Term term = Generator.generateTermWithId();
        term.setLabel(MultilingualString.create(TERM_NAME, Environment.LANGUAGE));
        when(idResolverMock.resolveIdentifier(config.getNamespace().getVocabulary(), VOCABULARY_NAME))
                .thenReturn(URI.create(VOCABULARY_URI));
        when(idResolverMock.buildNamespace(VOCABULARY_URI, config.getNamespace().getTerm().getSeparator()))
                .thenReturn(VOCABULARY_URI);
        when(termServiceMock.getRequiredReference(any())).thenReturn(term);
        final TermAssignments tai = new TermAssignments(term.getUri(), Generator.generateUri(), "Test", false);
        when(termServiceMock.getAssignmentInfo(term)).thenReturn(Collections.singletonList(tai));

        final MvcResult mvcResult =
                mockMvc.perform(get(PATH + VOCABULARY_NAME + "/terms/" + TERM_NAME + "/assignments")).andExpect(
                        status().isOk()).andReturn();
        final List<TermAssignments> result = readValue(mvcResult, new TypeReference<List<TermAssignments>>() {
        });
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(tai, result.get(0));
    }

    @Test
    void getAllExportsTermsToCsvWhenAcceptMediaTypeIsSetToCsv() throws Exception {
        when(idResolverMock.resolveIdentifier(config.getNamespace().getVocabulary(), VOCABULARY_NAME))
                .thenReturn(URI.create(VOCABULARY_URI));
        final cz.cvut.kbss.termit.model.Vocabulary vocabulary = Generator.generateVocabulary();
        vocabulary.setUri(URI.create(VOCABULARY_URI));
        when(termServiceMock.findVocabularyRequired(vocabulary.getUri())).thenReturn(vocabulary);
        final String content = String.join(",", Term.EXPORT_COLUMNS);
        final TypeAwareByteArrayResource export = new TypeAwareByteArrayResource(content.getBytes(),
                CsvUtils.MEDIA_TYPE,
                CsvUtils.FILE_EXTENSION);
        when(termServiceMock.exportGlossary(vocabulary, CsvUtils.MEDIA_TYPE)).thenReturn(Optional.of(export));

        mockMvc.perform(get(PATH + VOCABULARY_NAME + "/terms").accept(CsvUtils.MEDIA_TYPE)).andExpect(
                status().isOk());
        verify(termServiceMock).exportGlossary(vocabulary, CsvUtils.MEDIA_TYPE);
    }

    @Test
    void getAllReturnsCsvAsAttachmentWhenAcceptMediaTypeIsCsv() throws Exception {
        when(idResolverMock.resolveIdentifier(config.getNamespace().getVocabulary(), VOCABULARY_NAME))
                .thenReturn(URI.create(VOCABULARY_URI));
        final cz.cvut.kbss.termit.model.Vocabulary vocabulary = Generator.generateVocabulary();
        vocabulary.setUri(URI.create(VOCABULARY_URI));
        when(termServiceMock.findVocabularyRequired(vocabulary.getUri())).thenReturn(vocabulary);
        final String content = String.join(",", Term.EXPORT_COLUMNS);
        final TypeAwareByteArrayResource export = new TypeAwareByteArrayResource(content.getBytes(),
                CsvUtils.MEDIA_TYPE,
                CsvUtils.FILE_EXTENSION);
        when(termServiceMock.exportGlossary(vocabulary, CsvUtils.MEDIA_TYPE)).thenReturn(Optional.of(export));

        final MvcResult mvcResult = mockMvc
                .perform(get(PATH + VOCABULARY_NAME + "/terms").accept(CsvUtils.MEDIA_TYPE)).andReturn();
        assertThat(mvcResult.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION), containsString("attachment"));
        assertThat(mvcResult.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION),
                containsString("filename=\"" + VOCABULARY_NAME + CsvUtils.FILE_EXTENSION + "\""));
        assertEquals(content, mvcResult.getResponse().getContentAsString());
    }

    @Test
    void getAllExportsTermsToExcelWhenAcceptMediaTypeIsExcel() throws Exception {
        initNamespaceAndIdentifierResolution();
        final cz.cvut.kbss.termit.model.Vocabulary vocabulary = Generator.generateVocabulary();
        vocabulary.setUri(URI.create(VOCABULARY_URI));
        when(termServiceMock.findVocabularyRequired(vocabulary.getUri())).thenReturn(vocabulary);
        final TypeAwareByteArrayResource export = prepareExcel();
        when(termServiceMock.exportGlossary(vocabulary, Excel.MEDIA_TYPE)).thenReturn(Optional.of(export));

        mockMvc.perform(get(PATH + VOCABULARY_NAME + "/terms").accept(Excel.MEDIA_TYPE)).andExpect(
                status().isOk());
        verify(termServiceMock).exportGlossary(vocabulary, Excel.MEDIA_TYPE);
    }

    private TypeAwareByteArrayResource prepareExcel() throws Exception {
        final XSSFWorkbook wb = new XSSFWorkbook();
        final XSSFSheet s = wb.createSheet("test");
        s.createRow(0).createCell(0).setCellValue("test");
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        wb.write(bos);
        return new TypeAwareByteArrayResource(bos.toByteArray(), Excel.MEDIA_TYPE, Excel.FILE_EXTENSION);
    }

    @Test
    void getAllReturnsExcelAttachmentWhenAcceptMediaTypeIsExcel() throws Exception {
        initNamespaceAndIdentifierResolution();
        final cz.cvut.kbss.termit.model.Vocabulary vocabulary = Generator.generateVocabulary();
        vocabulary.setUri(URI.create(VOCABULARY_URI));
        when(termServiceMock.findVocabularyRequired(vocabulary.getUri())).thenReturn(vocabulary);
        final TypeAwareByteArrayResource export = prepareExcel();
        when(termServiceMock.exportGlossary(vocabulary, Excel.MEDIA_TYPE)).thenReturn(Optional.of(export));

        final MvcResult mvcResult = mockMvc
                .perform(get(PATH + VOCABULARY_NAME + "/terms").accept(Excel.MEDIA_TYPE)).andReturn();
        assertThat(mvcResult.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION), containsString("attachment"));
        assertThat(mvcResult.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION),
                containsString("filename=\"" + VOCABULARY_NAME + Excel.FILE_EXTENSION + "\""));
    }

    private void initNamespaceAndIdentifierResolution() {
        when(idResolverMock.resolveIdentifier(config.getNamespace().getVocabulary(), VOCABULARY_NAME))
                .thenReturn(URI.create(VOCABULARY_URI));
    }

    @Test
    void getAllRootsLoadsRootsFromCorrectPage() throws Exception {
        initNamespaceAndIdentifierResolution();
        final List<TermDto> terms = termsToDtos(Generator.generateTermsWithIds(5));
        when(termServiceMock.findVocabularyRequired(vocabulary.getUri())).thenReturn(vocabulary);
        when(termServiceMock.findAllRoots(eq(vocabulary), any(Pageable.class), anyCollection())).thenReturn(terms);
        mockMvc.perform(get(PATH + VOCABULARY_NAME + "/terms/roots").param(PAGE, "5").param(PAGE_SIZE, "100"))
               .andExpect(status().isOk());

        final ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(termServiceMock).findAllRoots(eq(vocabulary), captor.capture(), anyCollection());
        assertEquals(PageRequest.of(5, 100), captor.getValue());
    }

    @Test
    void getAllRootsCreatesDefaultPageRequestWhenPagingInfoIsNotSpecified() throws Exception {
        initNamespaceAndIdentifierResolution();
        final List<TermDto> terms = termsToDtos(Generator.generateTermsWithIds(5));
        when(termServiceMock.findVocabularyRequired(vocabulary.getUri())).thenReturn(vocabulary);
        when(termServiceMock.findAllRoots(eq(vocabulary), any(Pageable.class), anyCollection())).thenReturn(terms);
        mockMvc.perform(get(PATH + VOCABULARY_NAME + "/terms/roots")).andExpect(status().isOk());

        final ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(termServiceMock).findAllRoots(eq(vocabulary), captor.capture(), anyCollection());
        assertEquals(DEFAULT_PAGE_SPEC, captor.getValue());
    }

    @Test
    void getAllRootsWithoutVocabularyLoadsRootsFromCorrectPage() throws Exception {
        final List<TermDto> terms = termsToDtos(Generator.generateTermsWithIds(5));
        when(termServiceMock.findAllRoots(any(Pageable.class), anyCollection())).thenReturn(terms);
        mockMvc.perform(get("/terms/roots").param(PAGE, "5").param(PAGE_SIZE, "100"))
               .andExpect(status().isOk());

        final ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(termServiceMock).findAllRoots(captor.capture(), anyCollection());
        assertEquals(PageRequest.of(5, 100), captor.getValue());
    }

    @Test
    void getAllRootsWithoutVocabularyCreatesDefaultPageRequestWhenPagingInfoIsNotSpecified() throws Exception {
        final List<TermDto> terms = termsToDtos(Generator.generateTermsWithIds(5));
        when(termServiceMock.findAllRoots(any(Pageable.class), anyCollection())).thenReturn(terms);
        mockMvc.perform(get("/terms/roots")).andExpect(status().isOk());

        final ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(termServiceMock).findAllRoots(captor.capture(), anyCollection());
        assertEquals(DEFAULT_PAGE_SPEC, captor.getValue());
    }

    @Test
    void createRootTermPassesNewTermToService() throws Exception {
        initNamespaceAndIdentifierResolution();

        final Term newTerm = Generator.generateTermWithId();
        when(termServiceMock.findVocabularyRequired(vocabulary.getUri())).thenReturn(vocabulary);
        mockMvc.perform(post(PATH + VOCABULARY_NAME + "/terms").content(toJson(newTerm))
                                                               .contentType(MediaType.APPLICATION_JSON))
               .andExpect(status().isCreated());
        verify(termServiceMock).persistRoot(newTerm, vocabulary);
    }

    @Test
    void createRootTermReturnsLocationHeaderWithTermLocation() throws Exception {
        initNamespaceAndIdentifierResolution();

        final Term newTerm = Generator.generateTerm();
        newTerm.setUri(URI.create(NAMESPACE + TERM_NAME));
        newTerm.setLabel(MultilingualString.create(TERM_NAME, Environment.LANGUAGE));
        when(termServiceMock.findVocabularyRequired(vocabulary.getUri())).thenReturn(vocabulary);
        final MvcResult mvcResult = mockMvc
                .perform(post(PATH + VOCABULARY_NAME + "/terms").content(toJson(newTerm))
                                                                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated()).andReturn();
        verifyLocationEquals(PATH + VOCABULARY_NAME + "/terms/" + TERM_NAME, mvcResult);
    }

    @Test
    void createSubTermPassesNewTermToServiceTogetherWithParentTerm() throws Exception {
        initNamespaceAndIdentifierResolution();

        final Term parent = Generator.generateTerm();
        parent.setUri(URI.create(NAMESPACE + TERM_NAME));
        final String s = config.getNamespace().getTerm().getSeparator();
        when(idResolverMock.buildNamespace(VOCABULARY_URI, s))
                .thenReturn(VOCABULARY_URI + s);
        when(idResolverMock.resolveIdentifier(VOCABULARY_URI + s, TERM_NAME))
                .thenReturn(parent.getUri());
        when(termServiceMock.findRequired(parent.getUri())).thenReturn(parent);
        final Term newTerm = Generator.generateTermWithId();
        mockMvc.perform(
                post(PATH + VOCABULARY_NAME + "/terms/" + TERM_NAME + "/subterms").content(toJson(newTerm))
                                                                                  .contentType(MediaType.APPLICATION_JSON))
               .andExpect(status().isCreated());
        verify(termServiceMock).persistChild(newTerm, parent);
    }

    @Test
    void createSubTermReturnsLocationHeaderWithTermLocation() throws Exception {
        initNamespaceAndIdentifierResolution();

        final Term parent = Generator.generateTerm();
        parent.setUri(URI.create(NAMESPACE + TERM_NAME));
        final String s = config.getNamespace().getTerm().getSeparator();
        when(idResolverMock.buildNamespace(VOCABULARY_URI, s)).thenReturn(VOCABULARY_URI + s);
        when(idResolverMock.resolveIdentifier(VOCABULARY_URI + s, TERM_NAME))
                .thenReturn(parent.getUri());
        when(termServiceMock.findRequired(parent.getUri())).thenReturn(parent);
        final Term newTerm = Generator.generateTerm();
        final String name = "child-term";
        newTerm.setUri(URI.create(NAMESPACE + name));
        final MvcResult mvcResult = mockMvc.perform(
                post(PATH + VOCABULARY_NAME + "/terms/" + TERM_NAME + "/subterms").content(toJson(newTerm))
                                                                                  .contentType(MediaType.APPLICATION_JSON))
                                           .andExpect(status().isCreated()).andReturn();
        verifyLocationEquals(PATH + VOCABULARY_NAME + "/terms/" + name, mvcResult);
    }

    @Test
    void getAllExportsTermsToTurtleWhenAcceptMediaTypeIsTurtle() throws Exception {
        initNamespaceAndIdentifierResolution();
        final cz.cvut.kbss.termit.model.Vocabulary vocabulary = Generator.generateVocabulary();
        vocabulary.setUri(URI.create(VOCABULARY_URI));
        when(termServiceMock.findVocabularyRequired(vocabulary.getUri())).thenReturn(vocabulary);
        final TypeAwareByteArrayResource export = prepareTurtle();
        when(termServiceMock.exportGlossary(vocabulary, Turtle.MEDIA_TYPE)).thenReturn(Optional.of(export));

        mockMvc.perform(get(PATH + VOCABULARY_NAME + "/terms").accept(Turtle.MEDIA_TYPE)).andExpect(
                status().isOk());
        verify(termServiceMock).exportGlossary(vocabulary, Turtle.MEDIA_TYPE);
    }

    private TypeAwareByteArrayResource prepareTurtle() {
        final String content = "@base <http://example.org/> .\n" +
                "@prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .\n" +
                "@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .\n" +
                "@prefix foaf: <http://xmlns.com/foaf/0.1/> .\n" +
                "@prefix rel: <http://www.perceive.net/schemas/relationship/> .\n" +
                "\n" +
                "<#spiderman>\n" +
                "    rel:enemyOf <#green-goblin> ;\n" +
                "    a foaf:Person ;\n" +
                "    foaf:name \"Spiderman\", .";
        return new TypeAwareByteArrayResource(content.getBytes(), Turtle.MEDIA_TYPE, Turtle.FILE_EXTENSION);
    }

    @Test
    void getAllReturnsTurtleAttachmentWhenAcceptMediaTypeIsTurtle() throws Exception {
        initNamespaceAndIdentifierResolution();
        final cz.cvut.kbss.termit.model.Vocabulary vocabulary = Generator.generateVocabulary();
        vocabulary.setUri(URI.create(VOCABULARY_URI));
        when(termServiceMock.findVocabularyRequired(vocabulary.getUri())).thenReturn(vocabulary);
        final TypeAwareByteArrayResource export = prepareTurtle();
        when(termServiceMock.exportGlossary(vocabulary, Turtle.MEDIA_TYPE)).thenReturn(Optional.of(export));

        final MvcResult mvcResult = mockMvc
                .perform(get(PATH + VOCABULARY_NAME + "/terms").accept(Turtle.MEDIA_TYPE)).andReturn();
        assertThat(mvcResult.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION), containsString("attachment"));
        assertThat(mvcResult.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION),
                containsString("filename=\"" + VOCABULARY_NAME + Turtle.FILE_EXTENSION + "\""));
    }

    @Test
    void getByIdAtStandaloneEndpointResolvesTermIdentifierAndReturnsTerm() throws Exception {
        final URI termUri = URI.create(NAMESPACE + TERM_NAME);
        final Term term = Generator.generateTerm();
        when(idResolverMock.resolveIdentifier(NAMESPACE, TERM_NAME)).thenReturn(termUri);
        term.setUri(termUri);
        when(termServiceMock.findRequired(termUri)).thenReturn(term);
        final MvcResult mvcResult = mockMvc.perform(get("/terms/" + TERM_NAME).param(QueryParams.NAMESPACE, NAMESPACE))
                                           .andExpect(status().isOk()).andReturn();
        final Term result = readValue(mvcResult, Term.class);
        assertEquals(term, result);
        verify(idResolverMock).resolveIdentifier(NAMESPACE, TERM_NAME);
    }

    @Test
    void removeRemovesTermByIdentifier() throws Exception {
        final cz.cvut.kbss.termit.model.Vocabulary vocabulary = Generator.generateVocabulary();
        vocabulary.setUri(URI.create(VOCABULARY_URI));
        final Term term = Generator.generateTerm();
        final URI termUri = URI.create(NAMESPACE + TERM_NAME);
        term.setUri(termUri);
        when(config.getNamespace().getTerm().getSeparator()).thenReturn("/pojem");
        when(idResolverMock.resolveIdentifier(NAMESPACE, TERM_NAME)).thenReturn(termUri);
        when(idResolverMock.resolveIdentifier(Environment.BASE_URI, VOCABULARY_NAME))
                .thenReturn(URI.create(VOCABULARY_URI));
        when(idResolverMock.buildNamespace(VOCABULARY_URI, "/pojem")).thenReturn(NAMESPACE);
        when(termServiceMock.findRequired(termUri)).thenReturn(term);
        mockMvc.perform(delete("/vocabularies/" + VOCABULARY_NAME + "/terms/" + TERM_NAME)
                .param(QueryParams.NAMESPACE, Environment.BASE_URI))
               .andExpect(status().isNoContent());
    }

    @Test
    void removeThrowsNotFoundExceptionWhenTermDoesNotExist() throws Exception {
        final cz.cvut.kbss.termit.model.Vocabulary vocabulary = Generator.generateVocabulary();
        vocabulary.setUri(URI.create(VOCABULARY_URI));
        final Term term = Generator.generateTerm();
        final URI termUri = URI.create(NAMESPACE + TERM_NAME);
        term.setUri(termUri);
        when(config.getNamespace().getTerm().getSeparator()).thenReturn("/pojem");
        when(idResolverMock.resolveIdentifier(NAMESPACE, TERM_NAME)).thenReturn(termUri);
        when(idResolverMock.resolveIdentifier(Environment.BASE_URI, VOCABULARY_NAME))
                .thenReturn(URI.create(VOCABULARY_URI));
        when(idResolverMock.buildNamespace(VOCABULARY_URI, "/pojem")).thenReturn(NAMESPACE);
        when(termServiceMock.findRequired(termUri)).thenThrow(NotFoundException.class);
        mockMvc.perform(delete("/vocabularies/" + VOCABULARY_NAME + "/terms/" + TERM_NAME)
                .param(QueryParams.NAMESPACE, Environment.BASE_URI))
               .andExpect(status().isNotFound());
        verify(termServiceMock, never()).remove(term);
    }

    @Test
    void updateStandaloneUpdatesTerm() throws Exception {
        final URI termUri = URI.create(NAMESPACE + TERM_NAME);
        final Term term = Generator.generateTerm();
        when(idResolverMock.resolveIdentifier(NAMESPACE, TERM_NAME)).thenReturn(termUri);
        term.setUri(termUri);
        mockMvc.perform(
                put("/terms/" + TERM_NAME).param(QueryParams.NAMESPACE, NAMESPACE).content(toJson(term)).contentType(
                        MediaType.APPLICATION_JSON_VALUE)).andExpect(status().isNoContent());
        verify(idResolverMock).resolveIdentifier(NAMESPACE, TERM_NAME);
        verify(termServiceMock).update(term);
    }

    @Test
    void getSubTermsStandaloneLoadsSubTermsOfParentTerm() throws Exception {
        final URI termUri = URI.create(NAMESPACE + TERM_NAME);
        final Term term = Generator.generateTerm();
        term.setUri(termUri);
        when(idResolverMock.resolveIdentifier(NAMESPACE, TERM_NAME)).thenReturn(termUri);
        when(termServiceMock.findRequired(term.getUri())).thenReturn(term);
        final List<Term> children = Generator.generateTermsWithIds(3);
        when(termServiceMock.findSubTerms(term)).thenReturn(children);

        final MvcResult mvcResult = mockMvc
                .perform(get("/terms/" + TERM_NAME + "/subterms").param(QueryParams.NAMESPACE, NAMESPACE))
                .andExpect(status().isOk()).andReturn();
        final List<Term> result = readValue(mvcResult, new TypeReference<List<Term>>() {
        });
        assertEquals(children.size(), result.size());
        assertTrue(children.containsAll(result));
        verify(idResolverMock).resolveIdentifier(NAMESPACE, TERM_NAME);
        verify(termServiceMock).findSubTerms(term);
    }

    @Test
    void createSubTermStandalonePassesNewTermToServiceTogetherWithParentTerm() throws Exception {
        final URI termUri = URI.create(NAMESPACE + TERM_NAME);
        final Term term = Generator.generateTerm();
        term.setUri(termUri);
        when(idResolverMock.resolveIdentifier(NAMESPACE, TERM_NAME)).thenReturn(termUri);
        when(termServiceMock.findRequired(term.getUri())).thenReturn(term);

        final Term newTerm = Generator.generateTermWithId();
        mockMvc.perform(post("/terms/" + TERM_NAME + "/subterms").param(QueryParams.NAMESPACE, NAMESPACE)
                                                                 .content(toJson(newTerm))
                                                                 .contentType(MediaType.APPLICATION_JSON))
               .andExpect(status().isCreated());
        verify(termServiceMock).persistChild(newTerm, term);
    }

    @Test
    void createSubTermStandaloneReturnsCorrectLocationValue() throws Exception {
        final URI termUri = URI.create(NAMESPACE + TERM_NAME);
        final Term term = Generator.generateTerm();
        term.setUri(termUri);
        when(idResolverMock.resolveIdentifier(NAMESPACE, TERM_NAME)).thenReturn(termUri);
        when(termServiceMock.findRequired(term.getUri())).thenReturn(term);

        final Term newTerm = Generator.generateTerm();
        final String name = "child-term";
        newTerm.setUri(URI.create(NAMESPACE + name));
        final MvcResult mvcResult = mockMvc
                .perform(post("/terms/" + TERM_NAME + "/subterms").param(QueryParams.NAMESPACE, NAMESPACE)
                                                                  .content(toJson(newTerm))
                                                                  .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated()).andReturn();
        verifyLocationEquals("/terms/" + name, mvcResult);
    }

    @Test
    void getAssignmentInfoStandaloneGetsTermAssignmentInfoFromService() throws Exception {
        final URI termUri = URI.create(NAMESPACE + TERM_NAME);
        final Term term = Generator.generateTerm();
        term.setUri(termUri);
        when(idResolverMock.resolveIdentifier(NAMESPACE, TERM_NAME)).thenReturn(termUri);
        when(termServiceMock.getRequiredReference(any())).thenReturn(term);
        final TermAssignments tai = new TermAssignments(term.getUri(), Generator.generateUri(), "Test", false);
        when(termServiceMock.getAssignmentInfo(term)).thenReturn(Collections.singletonList(tai));

        final MvcResult mvcResult =
                mockMvc.perform(get("/terms/" + TERM_NAME + "/assignments").param(QueryParams.NAMESPACE, NAMESPACE))
                       .andExpect(status().isOk()).andReturn();
        final List<TermAssignments> result = readValue(mvcResult, new TypeReference<List<TermAssignments>>() {
        });
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(tai, result.get(0));
        verify(idResolverMock).resolveIdentifier(NAMESPACE, TERM_NAME);
        verify(termServiceMock).getRequiredReference(termUri);
        verify(termServiceMock).getAssignmentInfo(term);
    }

    @Test
    void getAllRootsWithPageSpecAndIncludeImportsGetsRootTermsIncludingImportedTermsFromService() throws Exception {
        when(idResolverMock.resolveIdentifier(config.getNamespace().getVocabulary(), VOCABULARY_NAME))
                .thenReturn(URI.create(VOCABULARY_URI));
        when(termServiceMock.findVocabularyRequired(vocabulary.getUri())).thenReturn(vocabulary);

        mockMvc.perform(
                get(PATH + VOCABULARY_NAME + "/terms/roots").param("includeImported", Boolean.TRUE.toString()))
               .andExpect(status().isOk());
        verify(termServiceMock).findAllRootsIncludingImported(vocabulary, DEFAULT_PAGE_SPEC, Collections.emptyList());
    }

    @Test
    void getAllWithSearchStringAndIncludeImportsGetsMatchingTermsIncludingImportedTermsFromService() throws Exception {
        when(idResolverMock.resolveIdentifier(config.getNamespace().getVocabulary(), VOCABULARY_NAME))
                .thenReturn(URI.create(VOCABULARY_URI));
        when(termServiceMock.findVocabularyRequired(vocabulary.getUri())).thenReturn(vocabulary);
        final String searchString = "test";
        mockMvc.perform(
                get(PATH + VOCABULARY_NAME + "/terms")
                        .param("includeImported", Boolean.TRUE.toString())
                        .param("searchString", searchString))
               .andExpect(status().isOk());
        verify(termServiceMock).findAllIncludingImported(searchString, vocabulary);
    }

    @Test
    void removeRemovesTermWithSpecifiedIdentifier() throws Exception {
        final URI termUri = URI.create(NAMESPACE + TERM_NAME);
        final Term toRemove = Generator.generateTerm();
        toRemove.setUri(termUri);
        final String s = config.getNamespace().getTerm().getSeparator();
        when(idResolverMock.resolveIdentifier(config.getNamespace().getVocabulary(), VOCABULARY_NAME))
                .thenReturn(URI.create(VOCABULARY_URI));
        when(idResolverMock.buildNamespace(VOCABULARY_URI, s))
                .thenReturn(NAMESPACE);
        when(idResolverMock.resolveIdentifier(NAMESPACE, TERM_NAME)).thenReturn(termUri);
        when(termServiceMock.findRequired(termUri)).thenReturn(toRemove);

        mockMvc.perform(
                delete(PATH + VOCABULARY_NAME + "/terms/" + TERM_NAME))
               .andExpect(status().isNoContent());
        verify(termServiceMock).remove(toRemove);
    }

    @Test
    void removeThrowsNotFoundWhenTermToRemoveDoesNotExist() throws Exception {
        final URI termUri = URI.create(NAMESPACE + TERM_NAME);
        final String s = config.getNamespace().getTerm().getSeparator();
        when(idResolverMock.resolveIdentifier(config.getNamespace().getVocabulary(), VOCABULARY_NAME))
                .thenReturn(URI.create(VOCABULARY_URI));
        when(idResolverMock.buildNamespace(VOCABULARY_URI, s))
                .thenReturn(NAMESPACE);
        when(idResolverMock.resolveIdentifier(NAMESPACE, TERM_NAME)).thenReturn(termUri);
        when(termServiceMock.findRequired(termUri)).thenThrow(NotFoundException.class);

        mockMvc.perform(
                delete(PATH + VOCABULARY_NAME + "/terms/" + TERM_NAME))
               .andExpect(status().isNotFound());
        verify(termServiceMock, never()).remove(any());
    }

    @Test
    void setTermDefinitionSourceSetsDefinitionSourceOfSpecifiedTermViaService() throws Exception {
        final URI termUri = URI.create(NAMESPACE + TERM_NAME);
        final Term term = Generator.generateTerm();
        term.setUri(termUri);
        when(idResolverMock.resolveIdentifier(NAMESPACE, TERM_NAME)).thenReturn(termUri);
        when(termServiceMock.getRequiredReference(termUri)).thenReturn(term);
        final TermDefinitionSource source = new TermDefinitionSource();
        final File file = Generator.generateFileWithId("test.html");
        source.setTarget(new FileOccurrenceTarget(file));

        mockMvc.perform(put("/terms/" + TERM_NAME + "/definition-source")
                .param(QueryParams.NAMESPACE, NAMESPACE)
                .content(toJson(source)).contentType(MediaType.APPLICATION_JSON))
               .andExpect(status().isNoContent());
        final ArgumentCaptor<TermDefinitionSource> captor = ArgumentCaptor.forClass(TermDefinitionSource.class);
        verify(termServiceMock).setTermDefinitionSource(eq(term), captor.capture());
        assertEquals(file.getUri(), captor.getValue().getTarget().getSource());
    }

    @Test
    void getHistoryReturnsListOfChangeRecordsForSpecifiedTerm() throws Exception {
        final URI termUri = initTermUriResolution();
        final Term term = Generator.generateTerm();
        term.setUri(termUri);
        when(termServiceMock.getRequiredReference(term.getUri())).thenReturn(term);
        final List<AbstractChangeRecord> records = generateChangeRecords(term);
        when(termServiceMock.getChanges(term)).thenReturn(records);

        final MvcResult mvcResult = mockMvc
                .perform(get(PATH + VOCABULARY_NAME + "/terms/" + TERM_NAME + "/history"))
                .andExpect(status().isOk()).andReturn();
        final List<AbstractChangeRecord> result = readValue(mvcResult, new TypeReference<List<AbstractChangeRecord>>() {
        });
        assertNotNull(result);
        assertEquals(records, result);
    }

    private List<AbstractChangeRecord> generateChangeRecords(Term term) {
        final User author = Generator.generateUserWithId();
        return IntStream.range(0, 5).mapToObj(i -> {
            final UpdateChangeRecord record = new UpdateChangeRecord(term);
            record.setAuthor(author);
            record.setChangedAttribute(URI.create(SKOS.PREF_LABEL));
            record.setTimestamp(Instant.ofEpochSecond(System.currentTimeMillis() + i * 1000L));
            return record;
        }).collect(Collectors.toList());
    }

    @Test
    void getHistoryStandaloneReturnsListOfChangeRecordsForSpecifiedTerm() throws Exception {
        final URI termUri = URI.create(NAMESPACE + TERM_NAME);
        final Term term = Generator.generateTerm();
        term.setUri(termUri);
        when(idResolverMock.resolveIdentifier(NAMESPACE, TERM_NAME)).thenReturn(termUri);
        when(termServiceMock.getRequiredReference(termUri)).thenReturn(term);
        final List<AbstractChangeRecord> records = generateChangeRecords(term);
        when(termServiceMock.getChanges(term)).thenReturn(records);

        final MvcResult mvcResult = mockMvc
                .perform(get("/terms/" + TERM_NAME + "/history").param(QueryParams.NAMESPACE, NAMESPACE))
                .andExpect(status().isOk())
                .andReturn();
        final List<AbstractChangeRecord> result = readValue(mvcResult, new TypeReference<List<AbstractChangeRecord>>() {
        });
        assertNotNull(result);
        assertEquals(records, result);
    }

    @Test
    void getAllRootsPassesProvidedIdentifiersOfTermsToIncludeToService() throws Exception {
        initNamespaceAndIdentifierResolution();
        final List<TermDto> terms = termsToDtos(Generator.generateTermsWithIds(5));
        when(termServiceMock.findVocabularyRequired(vocabulary.getUri())).thenReturn(vocabulary);
        when(termServiceMock.findAllRoots(eq(vocabulary), any(Pageable.class), anyCollection())).thenReturn(terms);
        final List<URI> toInclude = Arrays.asList(Generator.generateUri(), Generator.generateUri());
        mockMvc.perform(get(PATH + VOCABULARY_NAME + "/terms/roots").param("includeTerms",
                toInclude.stream().map(URI::toString)
                         .toArray(String[]::new)))
               .andExpect(status().isOk());

        verify(termServiceMock).findAllRoots(eq(vocabulary), any(Pageable.class), eq(toInclude));
    }

    @Test
    void getCommentsRetrievesCommentsForSpecifiedTerm() throws Exception {
        final URI termUri = initTermUriResolution();
        final Term term = Generator.generateTerm();
        term.setUri(termUri);
        when(termServiceMock.getRequiredReference(term.getUri())).thenReturn(term);
        final List<Comment> comments = generateComments(term);
        when(termServiceMock.getComments(term)).thenReturn(comments);

        final MvcResult mvcResult = mockMvc.perform(get(PATH + VOCABULARY_NAME + "/terms/" + TERM_NAME + "/comments"))
                                           .andExpect(status().isOk()).andReturn();
        final List<Comment> result = readValue(mvcResult, new TypeReference<List<Comment>>() {
        });
        assertEquals(comments, result);
        verify(termServiceMock).getComments(term);
    }

    private URI initTermUriResolutionForStandalone() {
        final String s = config.getNamespace().getTerm().getSeparator();
        when(idResolverMock.resolveIdentifier(config.getNamespace().getVocabulary(), VOCABULARY_NAME))
                .thenReturn(URI.create(VOCABULARY_URI));
        when(idResolverMock.buildNamespace(VOCABULARY_URI, s)).thenReturn(NAMESPACE);
        final URI termUri = URI.create(TERM_URI);
        when(idResolverMock.resolveIdentifier(NAMESPACE, TERM_NAME)).thenReturn(termUri);
        return termUri;
    }

    private static List<Comment> generateComments(Term term) {
        return IntStream.range(0, 5).mapToObj(i -> generateComment(term)).collect(Collectors.toList());
    }

    private static Comment generateComment(Term term) {
        final Comment c = new Comment();
        c.setContent("Comment " + Generator.randomInt());
        if (term != null) {
            c.setAsset(term.getUri());
            c.setCreated(new Date());
        }
        return c;
    }

    @Test
    void getCommentsStandaloneRetrievesCommentsForSpecifiedTerm() throws Exception {
        final URI termUri = URI.create(TERM_URI);
        final Term term = Generator.generateTerm();
        term.setUri(termUri);
        when(idResolverMock.resolveIdentifier(NAMESPACE, TERM_NAME)).thenReturn(termUri);
        when(termServiceMock.getRequiredReference(term.getUri())).thenReturn(term);
        final List<Comment> comments = generateComments(term);
        when(termServiceMock.getComments(term)).thenReturn(comments);

        final MvcResult mvcResult = mockMvc
                .perform(get("/terms/" + TERM_NAME + "/comments").param(QueryParams.NAMESPACE, NAMESPACE))
                .andExpect(status().isOk()).andReturn();
        final List<Comment> result = readValue(mvcResult, new TypeReference<List<Comment>>() {
        });
        assertEquals(comments, result);
        verify(termServiceMock).getComments(term);
    }

    @Test
    void addCommentAddsSpecifiedCommentToSpecifiedTerm() throws Exception {
        final URI termUri = initTermUriResolution();
        final Term term = Generator.generateTerm();
        term.setUri(termUri);
        when(termServiceMock.getRequiredReference(term.getUri())).thenReturn(term);
        final Comment comment = generateComment(null);
        comment.setUri(Generator.generateUri());

        mockMvc.perform(post("/vocabularies/" + VOCABULARY_NAME + "/terms/" + TERM_NAME + "/comments")
                .content(toJson(comment))
                .contentType(MediaType.APPLICATION_JSON))
               .andExpect(status().isCreated());
        verify(termServiceMock).addComment(comment, term);
    }

    @Test
    void addCommentReturnsLocationHeaderWithGeneratedIdentifier() throws Exception {
        final URI termUri = initTermUriResolutionForStandalone();
        final Term term = Generator.generateTerm();
        term.setUri(termUri);
        when(termServiceMock.getRequiredReference(term.getUri())).thenReturn(term);
        final Comment comment = generateComment(null);
        final String name = "comment-12345";
        final String namespace = Vocabulary.ONTOLOGY_IRI_glosar + "/comment/";
        comment.setUri(URI.create(namespace + name));

        final MvcResult mvcResult = mockMvc
                .perform(post("/vocabularies/" + VOCABULARY_NAME + "/terms/" + TERM_NAME + "/comments")
                        .content(toJson(comment))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated()).andReturn();
        verifyLocationEquals("/comments/" + name, mvcResult);
    }

    @Test
    void addCommentStandaloneAddsSpecifiedCommentToSpecifiedTerm() throws Exception {
        final URI termUri = URI.create(TERM_URI);
        when(idResolverMock.resolveIdentifier(NAMESPACE, TERM_NAME)).thenReturn(termUri);
        final Term term = Generator.generateTerm();
        term.setUri(termUri);
        when(termServiceMock.getRequiredReference(term.getUri())).thenReturn(term);
        final Comment comment = generateComment(null);
        comment.setUri(Generator.generateUri());

        mockMvc.perform(post("/terms/" + TERM_NAME + "/comments")
                .queryParam(QueryParams.NAMESPACE, NAMESPACE)
                .content(toJson(comment))
                .contentType(MediaType.APPLICATION_JSON))
               .andExpect(status().isCreated());
        verify(termServiceMock).addComment(comment, term);
    }

    @Test
    void addCommentStandaloneReturnsLocationHeaderWithGeneratedIdentifier() throws Exception {
        final URI termUri = URI.create(TERM_URI);
        when(idResolverMock.resolveIdentifier(NAMESPACE, TERM_NAME)).thenReturn(termUri);
        final Term term = Generator.generateTerm();
        term.setUri(termUri);
        when(termServiceMock.getRequiredReference(term.getUri())).thenReturn(term);
        final Comment comment = generateComment(null);
        final String name = "comment-12345";
        final String namespace = Vocabulary.ONTOLOGY_IRI_glosar + "/comment/";
        comment.setUri(URI.create(namespace + name));

        final MvcResult mvcResult = mockMvc
                .perform(post("/terms/" + TERM_NAME + "/comments")
                        .queryParam(QueryParams.NAMESPACE, NAMESPACE)
                        .content(toJson(comment))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated()).andReturn();
        verifyLocationEquals("/comments/" + name, mvcResult);
    }

    @Test
    void getAllTermsCallsServiceWithSearchString() throws Exception {
        final String searchString = "test";
        mockMvc.perform(get("/terms").param("searchString", searchString)).andExpect(status().isOk());
        verify(termServiceMock).findAll(searchString);
    }

    @Test
    void checkTermsRetrievesNumberOfTermsInVocabularyWithSpecifiedIdentifier() throws Exception {
        when(idResolverMock.resolveIdentifier(config.getNamespace().getVocabulary(), VOCABULARY_NAME))
                .thenReturn(URI.create(VOCABULARY_URI));
        when(termServiceMock.getRequiredVocabularyReference(vocabulary.getUri())).thenReturn(vocabulary);
        final Integer termCount = Generator.randomInt(0, 200);
        when(termServiceMock.getTermCount(vocabulary)).thenReturn(termCount);

        final MvcResult mvcResult = mockMvc.perform(head(PATH + VOCABULARY_NAME + "/terms")).andExpect(status().isOk())
                                           .andReturn();
        final String countHeader = mvcResult.getResponse().getHeader(Constants.X_TOTAL_COUNT_HEADER);
        assertNotNull(countHeader);
        assertEquals(termCount, Integer.parseInt(countHeader));
    }
}
