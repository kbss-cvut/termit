/*
 * TermIt
 * Copyright (C) 2025 Czech Technical University in Prague
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.rest.readonly;

import com.fasterxml.jackson.core.type.TypeReference;
import cz.cvut.kbss.termit.dto.listing.TermDto;
import cz.cvut.kbss.termit.dto.readonly.ReadOnlyTerm;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.assignment.TermOccurrence;
import cz.cvut.kbss.termit.model.comment.Comment;
import cz.cvut.kbss.termit.rest.BaseControllerTestRunner;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.readonly.ReadOnlyTermService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.Utils;
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
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static cz.cvut.kbss.termit.environment.Environment.termsToDtos;
import static cz.cvut.kbss.termit.environment.Generator.generateComments;
import static cz.cvut.kbss.termit.environment.util.ContainsSameEntities.containsSameEntities;
import static cz.cvut.kbss.termit.util.Constants.DEFAULT_PAGE_SPEC;
import static cz.cvut.kbss.termit.util.Constants.QueryParams.PAGE;
import static cz.cvut.kbss.termit.util.Constants.QueryParams.PAGE_SIZE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ReadOnlyTermControllerTest extends BaseControllerTestRunner {

    private static final String PATH = "/public/vocabularies/";
    private static final String VOCABULARY_NAME = "metropolitan-plan";
    private static final String TERM_NAME = "locality";
    private static final String VOCABULARY_URI = Environment.BASE_URI + "/" + VOCABULARY_NAME;
    private static final String NAMESPACE = VOCABULARY_URI + "/pojem/";

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Configuration config;

    @Mock
    private ReadOnlyTermService termService;

    @Mock
    private IdentifierResolver idResolver;

    private Vocabulary vocabulary;

    @InjectMocks
    private ReadOnlyTermController sut;

    @BeforeEach
    void setUp() {
        this.vocabulary = Generator.generateVocabulary();
        vocabulary.setUri(URI.create(VOCABULARY_URI));
        setUp(sut);
        when(config.getNamespace().getVocabulary()).thenReturn(Environment.BASE_URI + "/");
    }

    private URI initTermUriResolution() {
        final URI termUri = URI.create(Environment.BASE_URI + "/" + VOCABULARY_NAME +
                                               config.getNamespace().getTerm().getSeparator() + "/" + TERM_NAME);
        when(idResolver.resolveIdentifier(config.getNamespace().getVocabulary(), VOCABULARY_NAME))
                .thenReturn(URI.create(VOCABULARY_URI));
        when(idResolver.buildNamespace(eq(VOCABULARY_URI), any())).thenReturn(NAMESPACE);
        when(idResolver.resolveIdentifier(NAMESPACE, TERM_NAME)).thenReturn(termUri);
        return termUri;
    }

    @Test
    void getAllReturnsAllTermsFromVocabularyFromService() throws Exception {
        when(idResolver.resolveIdentifier(config.getNamespace()
                                                .getVocabulary(), VOCABULARY_NAME)).thenReturn(
                URI.create(VOCABULARY_URI));
        final List<TermDto> terms = generateTerms();
        when(termService.findVocabularyRequired(URI.create(VOCABULARY_URI))).thenReturn(vocabulary);
        when(termService.findAll(any(), any(Pageable.class))).thenReturn(terms);

        final MvcResult mvcResult = mockMvc.perform(get(PATH + VOCABULARY_NAME + "/terms")).andExpect(status().isOk())
                                           .andReturn();
        final List<TermDto> result = readValue(mvcResult, new TypeReference<>() {
        });
        assertEquals(terms, result);
        final ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(termService).findAll(eq(vocabulary), captor.capture());
    }

    private List<TermDto> generateTerms() {
        return termsToDtos(Generator.generateTermsWithIds(5));
    }

    @Test
    void getAllWithSearchStringUsesServiceToRetrieveMatchingTermsAndReturnsThem() throws Exception {
        when(idResolver.resolveIdentifier(Environment.BASE_URI, VOCABULARY_NAME)).thenReturn(
                URI.create(VOCABULARY_URI));
        final List<TermDto> terms = generateTerms();
        when(termService.findVocabularyRequired(URI.create(VOCABULARY_URI))).thenReturn(vocabulary);
        when(termService.findAll(any(), any(), any(Pageable.class))).thenReturn(terms);
        final String searchString = "test";

        final MvcResult mvcResult = mockMvc.perform((get(PATH + VOCABULARY_NAME + "/terms"))
                                                            .param(Constants.QueryParams.NAMESPACE,
                                                                   Environment.BASE_URI)
                                                            .param("searchString", searchString))
                                           .andExpect(status().isOk())
                                           .andReturn();
        final List<TermDto> result = readValue(mvcResult, new TypeReference<>() {
        });
        assertEquals(terms, result);
        final ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(termService).findAll(eq(searchString), eq(vocabulary), captor.capture());
    }

    @Test
    void getAllWithSearchStringAndIncludeImportedUsesServiceToRetrieveMatchingTermsIncludingImportedOnesAndReturnsThem()
            throws Exception {
        when(idResolver.resolveIdentifier(Environment.BASE_URI, VOCABULARY_NAME)).thenReturn(
                URI.create(VOCABULARY_URI));
        final List<TermDto> terms = generateTerms();
        when(termService.findVocabularyRequired(URI.create(VOCABULARY_URI))).thenReturn(vocabulary);
        when(termService.findAllIncludingImported(any(), any(), any(Pageable.class))).thenReturn(terms);
        final String searchString = "test";

        final MvcResult mvcResult = mockMvc.perform((get(PATH + VOCABULARY_NAME + "/terms"))
                                                            .param(Constants.QueryParams.NAMESPACE,
                                                                   Environment.BASE_URI)
                                                            .param("searchString", searchString)
                                                            .param("includeImported", Boolean.TRUE.toString()))
                                           .andExpect(status().isOk())
                                           .andReturn();
        final List<TermDto> result = readValue(mvcResult, new TypeReference<>() {
        });
        assertEquals(terms, result);
        final ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(termService).findAllIncludingImported(eq(searchString), eq(vocabulary), captor.capture());
    }

    @Test
    void getAllRootsLoadsRootsFromCorrectPage() throws Exception {
        when(idResolver.resolveIdentifier(config.getNamespace()
                                                .getVocabulary(), VOCABULARY_NAME)).thenReturn(
                URI.create(VOCABULARY_URI));
        final List<TermDto> terms = generateTerms();
        when(termService.findVocabularyRequired(vocabulary.getUri())).thenReturn(vocabulary);
        when(termService.findAllRoots(eq(vocabulary), any(Pageable.class))).thenReturn(terms);
        mockMvc.perform(get(PATH + VOCABULARY_NAME + "/terms/roots").param(PAGE, "5").param(PAGE_SIZE, "100"))
               .andExpect(status().isOk());

        final ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(termService).findAllRoots(eq(vocabulary), captor.capture());
        assertEquals(PageRequest.of(5, 100), captor.getValue());
    }

    @Test
    void getAllRootsCreatesDefaultPageRequestWhenPagingInfoIsNotSpecified() throws Exception {
        when(idResolver.resolveIdentifier(config.getNamespace()
                                                .getVocabulary(), VOCABULARY_NAME)).thenReturn(
                URI.create(VOCABULARY_URI));
        final List<TermDto> terms = generateTerms();
        when(termService.findVocabularyRequired(vocabulary.getUri())).thenReturn(vocabulary);
        when(termService.findAllRoots(eq(vocabulary), any(Pageable.class))).thenReturn(terms);
        mockMvc.perform(get(PATH + VOCABULARY_NAME + "/terms/roots")).andExpect(status().isOk());

        final ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(termService).findAllRoots(eq(vocabulary), captor.capture());
        assertEquals(DEFAULT_PAGE_SPEC, captor.getValue());
    }

    @Test
    void getAllRootsRetrievesRootTermsIncludingImportedWhenParameterIsSpecified() throws Exception {
        when(idResolver.resolveIdentifier(config.getNamespace()
                                                .getVocabulary(), VOCABULARY_NAME)).thenReturn(
                URI.create(VOCABULARY_URI));
        final List<TermDto> terms = generateTerms();
        when(termService.findVocabularyRequired(vocabulary.getUri())).thenReturn(vocabulary);
        when(termService.findAllRootsIncludingImported(eq(vocabulary), any(Pageable.class))).thenReturn(terms);
        final MvcResult mvcResult = mockMvc
                .perform(get(PATH + VOCABULARY_NAME + "/terms/roots").param("includeImported", Boolean.TRUE.toString()))
                .andExpect(status().isOk()).andReturn();
        final List<TermDto> result = readValue(mvcResult, new TypeReference<>() {
        });
        assertEquals(terms, result);
        verify(termService).findAllRootsIncludingImported(eq(vocabulary), any(PageRequest.class));
    }

    @Test
    void getByIdRetrievesTermFromService() throws Exception {
        final ReadOnlyTerm term = new ReadOnlyTerm(Generator.generateTerm());
        term.setUri(URI.create(NAMESPACE + TERM_NAME));
        when(config.getNamespace().getTerm().getSeparator()).thenReturn("/pojem");
        when(idResolver.buildNamespace(VOCABULARY_URI, "/pojem")).thenReturn(NAMESPACE);
        when(idResolver.resolveIdentifier(Environment.BASE_URI, VOCABULARY_NAME)).thenReturn(
                URI.create(VOCABULARY_URI));
        when(idResolver.resolveIdentifier(NAMESPACE, TERM_NAME)).thenReturn(term.getUri());
        when(termService.findRequired(any())).thenReturn(term);
        final MvcResult mvcResult = mockMvc.perform(get(PATH + VOCABULARY_NAME + "/terms/" + TERM_NAME).param(
                Constants.QueryParams.NAMESPACE, Environment.BASE_URI)).andExpect(status().isOk()).andReturn();
        final ReadOnlyTerm result = readValue(mvcResult, ReadOnlyTerm.class);
        assertEquals(term, result);
        verify(termService).findRequired(term.getUri());
    }

    @Test
    void getByIdReturnsNotFoundWhenNotFoundExceptionIsThrownByService() throws Exception {
        when(idResolver.resolveIdentifier(Environment.BASE_URI, VOCABULARY_NAME)).thenReturn(
                URI.create(VOCABULARY_URI));
        when(termService.findRequired(any())).thenThrow(NotFoundException.class);
        mockMvc.perform(get(PATH + VOCABULARY_NAME + "/terms/" + TERM_NAME).param(
                Constants.QueryParams.NAMESPACE, Environment.BASE_URI)).andExpect(status().isNotFound());
    }

    @Test
    void getSubTermsRetrievesSubTermsOfTermFromService() throws Exception {
        final ReadOnlyTerm term = new ReadOnlyTerm(Generator.generateTerm());
        term.setUri(URI.create(NAMESPACE + TERM_NAME));
        when(config.getNamespace().getTerm().getSeparator()).thenReturn("/pojem");
        when(idResolver.buildNamespace(VOCABULARY_URI, "/pojem")).thenReturn(NAMESPACE);
        when(idResolver.resolveIdentifier(NAMESPACE, TERM_NAME)).thenReturn(term.getUri());
        when(idResolver.resolveIdentifier(Environment.BASE_URI, VOCABULARY_NAME)).thenReturn(
                URI.create(VOCABULARY_URI));
        when(termService.findRequired(any())).thenReturn(term);
        final List<TermDto> subTerms = Generator.generateTermsWithIds(5).stream()
                                                     .map(TermDto::new)
                                                     .toList();
        when(termService.findSubTerms(term)).thenReturn(subTerms);

        final MvcResult mvcResult = mockMvc
                .perform(get(PATH + VOCABULARY_NAME + "/terms/" + TERM_NAME + "/subterms").param(
                        Constants.QueryParams.NAMESPACE, Environment.BASE_URI)).andExpect(status().isOk()).andReturn();
        final List<TermDto> result = readValue(mvcResult, new TypeReference<>() {
        });
        assertEquals(subTerms, result);
        verify(termService).findSubTerms(term);
    }

    @Test
    void getCommentsRetrievesCommentsForSpecifiedTermUsingDefaultTimeInterval() throws Exception {
        final URI termUri = initTermUriResolution();
        final Term term = Generator.generateTerm();
        term.setUri(termUri);
        when(termService.getReference(term.getUri())).thenReturn(term);
        final List<Comment> comments = generateComments(term);
        when(termService.getComments(eq(term), any(Instant.class), any(Instant.class))).thenReturn(comments);

        final MvcResult mvcResult = mockMvc.perform(get(PATH + VOCABULARY_NAME + "/terms/" + TERM_NAME + "/comments"))
                                           .andExpect(status().isOk()).andReturn();
        final List<Comment> result = readValue(mvcResult, new TypeReference<>() {
        });
        assertEquals(comments, result);
        final ArgumentCaptor<Instant> toCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(termService).getComments(eq(term), eq(Constants.EPOCH_TIMESTAMP), toCaptor.capture());
        assertThat(Utils.timestamp().getEpochSecond() - toCaptor.getValue().getEpochSecond(), lessThan(10L));
    }

    @Test
    void getCommentsRetrievesCommentsInSpecifiedTimeInterval() throws Exception {
        final URI termUri = initTermUriResolution();
        final Term term = Generator.generateTerm();
        term.setUri(termUri);
        when(termService.getReference(term.getUri())).thenReturn(term);
        final List<Comment> comments = generateComments(term);
        when(termService.getComments(eq(term), any(Instant.class), any(Instant.class))).thenReturn(comments);
        final Instant from = Utils.timestamp().minus(Generator.randomInt(50, 100), ChronoUnit.DAYS);
        final Instant to = Utils.timestamp().minus(Generator.randomInt(0, 30), ChronoUnit.DAYS);

        final MvcResult mvcResult = mockMvc.perform(get(PATH + VOCABULARY_NAME + "/terms/" + TERM_NAME + "/comments")
                                                            .param("from", from.toString())
                                                            .param("to", to.toString()))
                                           .andExpect(status().isOk()).andReturn();
        final List<Comment> result = readValue(mvcResult, new TypeReference<>() {
        });
        assertEquals(comments, result);
        verify(termService).getComments(term, from, to);
    }

    @Test
    void getDefinitionallyRelatedOfRetrievesDefinitionalOccurrencesOfSpecifiedTerm() throws Exception {
        final URI termUri = initTermUriResolution();
        final Term term = Generator.generateTerm();
        term.setUri(termUri);
        when(termService.getReference(termUri)).thenReturn(term);
        final List<TermOccurrence> occurrences = generateOccurrences(term, true);
        when(termService.getDefinitionallyRelatedOf(term)).thenReturn(occurrences);

        final MvcResult mvcResult = mockMvc.perform(
                                                   get(PATH + VOCABULARY_NAME + "/terms/" + TERM_NAME + "/def-related-of"))
                                           .andExpect(status().isOk()).andReturn();
        final List<TermOccurrence> result = readValue(mvcResult, new TypeReference<>() {
        });
        assertThat(result, containsSameEntities(occurrences));
        verify(termService).getReference(termUri);
        verify(termService).getDefinitionallyRelatedOf(term);
    }

    private static List<TermOccurrence> generateOccurrences(Term term, boolean of) {
        return IntStream.range(0, 5)
                        .mapToObj(i -> {
                            final Term t = of ? term : Generator.generateTermWithId();
                            final Term target = of ? Generator.generateTermWithId() : term;
                            final TermOccurrence o = Generator.generateTermOccurrence(t, target, false);
                            o.setUri(Generator.generateUri());
                            return o;
                        })
                        .collect(Collectors.toList());
    }

    @Test
    void getDefinitionallyRelatedTargetingRetrievesDefinitionalOccurrencesTargetingSpecifiedTerm() throws Exception {
        final URI termUri = initTermUriResolution();
        final Term term = Generator.generateTerm();
        term.setUri(termUri);
        when(termService.getReference(termUri)).thenReturn(term);
        final List<TermOccurrence> occurrences = generateOccurrences(term, false);
        when(termService.getDefinitionallyRelatedTargeting(term)).thenReturn(occurrences);

        final MvcResult mvcResult = mockMvc.perform(
                                                   get(PATH + VOCABULARY_NAME + "/terms/" + TERM_NAME + "/def-related-target"))
                                           .andExpect(status().isOk()).andReturn();
        final List<TermOccurrence> result = readValue(mvcResult, new TypeReference<>() {
        });
        assertThat(result, containsSameEntities(occurrences));
        verify(termService).getReference(termUri);
        verify(termService).getDefinitionallyRelatedTargeting(term);
    }
}
