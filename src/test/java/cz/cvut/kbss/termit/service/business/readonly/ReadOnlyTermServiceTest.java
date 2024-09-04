/*
 * TermIt
 * Copyright (C) 2023 Czech Technical University in Prague
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
package cz.cvut.kbss.termit.service.business.readonly;

import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.dto.listing.TermDto;
import cz.cvut.kbss.termit.dto.readonly.ReadOnlyTerm;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.assignment.TermOccurrence;
import cz.cvut.kbss.termit.model.comment.Comment;
import cz.cvut.kbss.termit.service.business.TermService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.Utils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static cz.cvut.kbss.termit.environment.Environment.termsToDtos;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReadOnlyTermServiceTest {

    @Mock
    private TermService termService;

    @Mock
    private Configuration configuration;

    @InjectMocks
    private ReadOnlyTermService sut;

    @Test
    void getVocabularyReferenceRetrievesReferenceToVocabularyFromService() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        when(termService.findVocabularyRequired(any())).thenReturn(vocabulary);

        final Vocabulary result = sut.findVocabularyRequired(vocabulary.getUri());
        assertEquals(vocabulary, result);
        verify(termService).findVocabularyRequired(vocabulary.getUri());
    }

    @Test
    void findAllRetrievesAllTermsFromService() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final List<TermDto> terms = termsToDtos(Generator.generateTermsWithIds(5));
        when(termService.findAll(any(Vocabulary.class))).thenReturn(terms);

        final List<TermDto> result = sut.findAll(vocabulary);
        assertEquals(terms, result);
        verify(termService).findAll(vocabulary);
    }

    @Test
    void findAllBySearchStringSearchesForTermsViaServiceAndTransformsResultsToReadOnlyVersion() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final List<TermDto> terms = termsToDtos(Generator.generateTermsWithIds(5));
        final String searchString = "test";
        when(termService.findAll(anyString(), any())).thenReturn(terms);

        final List<TermDto> result = sut.findAll(searchString, vocabulary);
        assertEquals(terms, result);
        verify(termService).findAll(searchString, vocabulary);
    }

    @Test
    void findAllIncludingImportedBySearchStringSearchesForTermsViaServiceAndTransformsResultsToReadOnlyVersion() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final List<TermDto> terms = termsToDtos(Generator.generateTermsWithIds(5));
        final String searchString = "test";
        when(termService.findAllIncludingImported(anyString(), any())).thenReturn(terms);

        final List<TermDto> result = sut.findAllIncludingImported(searchString, vocabulary);
        assertEquals(terms, result);
        verify(termService).findAllIncludingImported(searchString, vocabulary);
    }

    @Test
    void findAllRootsGetsRootTermsFromServiceAndTransformsThemToReadOnlyVersion() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final List<TermDto> terms = termsToDtos(Generator.generateTermsWithIds(5));
        final Pageable pageSpec = PageRequest.of(1, 10);
        when(termService.findAllRoots(any(), any(), anyCollection())).thenReturn(terms);

        final List<TermDto> result = sut.findAllRoots(vocabulary, pageSpec);
        assertEquals(terms, result);
        verify(termService).findAllRoots(vocabulary, pageSpec, Collections.emptyList());
    }

    @Test
    void findAllRootsIncludingImportedGetsRootTermsFromServiceAndTransformsThemToReadOnlyVersion() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final List<TermDto> terms = termsToDtos(Generator.generateTermsWithIds(5));
        final Pageable pageSpec = PageRequest.of(1, 10);
        when(termService.findAllRootsIncludingImported(any(), any(), anyCollection())).thenReturn(terms);

        final List<TermDto> result = sut.findAllRootsIncludingImported(vocabulary, pageSpec);
        assertEquals(terms, result);
        verify(termService).findAllRootsIncludingImported(vocabulary, pageSpec, Collections.emptyList());
    }

    @Test
    void findRequiredRetrievesRequiredInstanceBySpecifiedIdentifierAndTransformsItToReadOnlyVersion() {
        when(configuration.getPublicView()).thenReturn(new Configuration.PublicView());

        final Term term = Generator.generateTermWithId();
        when(termService.findRequired(any())).thenReturn(term);

        final ReadOnlyTerm result = sut.findRequired(term.getUri());
        assertNotNull(result);
        assertEquals(new ReadOnlyTerm(term), result);
        verify(termService).findRequired(term.getUri());
    }

    @Test
    void findSubTermsRetrievesSubTermsOfSpecifiedTermFromServiceAndTransformsThemToReadOnlyVersion() {
        when(configuration.getPublicView()).thenReturn(new Configuration.PublicView());

        final Term term = Generator.generateTermWithId();
        final List<Term> subTerms = Generator.generateTermsWithIds(3);
        term.setSubTerms(subTerms.stream().map(TermInfo::new).collect(Collectors.toSet()));
        when(termService.findSubTerms(any())).thenReturn(subTerms);

        final List<ReadOnlyTerm> result = sut.findSubTerms(new ReadOnlyTerm(term));
        assertEquals(subTerms.stream().map(ReadOnlyTerm::new).collect(Collectors.toList()), result);
        final ArgumentCaptor<Term> captor = ArgumentCaptor.forClass(Term.class);
        verify(termService).findSubTerms(captor.capture());
        final Term arg = captor.getValue();
        assertEquals(term.getUri(), arg.getUri());
        assertEquals(subTerms.stream().map(TermInfo::new).collect(Collectors.toSet()), arg.getSubTerms());
    }

    @Test
    void getCommentsRetrievesCommentsForSpecifiedTerm() {
        final Term term = Generator.generateTermWithId();
        final Comment comment = new Comment();
        comment.setAsset(term.getUri());
        comment.setCreated(Utils.timestamp());
        when(termService.getComments(eq(term), any(Instant.class), any(Instant.class))).thenReturn(Collections.singletonList(comment));
        final Instant from = Constants.EPOCH_TIMESTAMP;
        final Instant to = Utils.timestamp();

        final List<Comment> result = sut.getComments(term, from, to);
        assertEquals(Collections.singletonList(comment), result);
        verify(termService).getComments(term, from, to);
    }

    @Test
    void getDefinitionallyRelatedOfRetrievesDefinitionallyRelatedOccurrencesFromTermService() {
        final Term term = Generator.generateTermWithId();
        final List<TermOccurrence> occurrences = generateOccurrences(term, true);
        when(termService.getDefinitionallyRelatedOf(term)).thenReturn(occurrences);
        final List<TermOccurrence> result = sut.getDefinitionallyRelatedOf(term);
        assertEquals(occurrences, result);
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
    void getDefinitionallyRelatedTargetingRetrievesDefinitionallyRelatedOccurrencesFromTermService() {
        final Term term = Generator.generateTermWithId();
        final List<TermOccurrence> occurrences = generateOccurrences(term, false);
        when(termService.getDefinitionallyRelatedTargeting(term)).thenReturn(occurrences);
        final List<TermOccurrence> result = sut.getDefinitionallyRelatedTargeting(term);
        assertEquals(occurrences, result);
        verify(termService).getDefinitionallyRelatedTargeting(term);
    }

    @Test
    void findSnapshotsRetrievesSnapshotsOfSpecifiedTerm() {
        final Term term = Generator.generateTermWithId();
        final ReadOnlyTerm asset = new ReadOnlyTerm(term);
        final List<Snapshot> snapshots = IntStream.range(0, 3).mapToObj(i -> Generator.generateSnapshot(term))
                                                  .collect(Collectors.toList());
        when(termService.findSnapshots(term)).thenReturn(snapshots);

        final List<Snapshot> result = sut.findSnapshots(asset);
        assertEquals(snapshots, result);
        verify(termService).findSnapshots(term);
    }

    @Test
    void findVersionValidAtRetrievesTermVersionsValidAtSpecifiedTimestamp() {
        when(configuration.getPublicView()).thenReturn(new Configuration.PublicView());
        final Term term = Generator.generateTermWithId();
        final ReadOnlyTerm asset = new ReadOnlyTerm(term);
        final Term version = Generator.generateTermWithId();
        final Instant timestamp = Instant.now();
        when(termService.findVersionValidAt(term, timestamp)).thenReturn(version);

        final ReadOnlyTerm result = sut.findVersionValidAt(asset, timestamp);
        assertEquals(new ReadOnlyTerm(version), result);
        verify(termService).findVersionValidAt(term, timestamp);
    }

    @Test
    void findVersionAtReturnsReadOnlyTermWithWhitelistedProperties() {
        final Term term = Generator.generateTermWithId();
        term.setProperties(new HashMap<>());
        term.getProperties().put(DC.Terms.REFERENCES, Collections.singleton(Generator.generateUri().toString()));
        // This one is not whitelisted, so it will not be exported
        term.getProperties().put(DC.Elements.DATE, Collections.singleton(Instant.now().toString()));
        final Configuration.PublicView whitelistedProps = new Configuration.PublicView();
        whitelistedProps.setWhiteListProperties(Collections.singleton(DC.Terms.REFERENCES));
        when(configuration.getPublicView()).thenReturn(whitelistedProps);
        final Instant timestamp = Instant.now();
        when(termService.findVersionValidAt(term, timestamp)).thenReturn(term);

        final ReadOnlyTerm result = sut.findVersionValidAt(new ReadOnlyTerm(term), timestamp);
        assertThat(result.getProperties(), hasEntry(DC.Terms.REFERENCES, term.getProperties()
                                                                             .get(DC.Terms.REFERENCES)));
        assertThat(result.getProperties(), not(hasEntry(DC.Elements.DATE, term.getProperties().get(DC.Elements.DATE))));
    }

    @Test
    void findSubTermsPassesTermWithVocabularyToUnderlyingServiceForTermSubTermResolution() {
        when(configuration.getPublicView()).thenReturn(new Configuration.PublicView());
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();

        final Term term = Generator.generateTermWithId();
        term.setVocabulary(vocabulary.getUri());
        final List<Term> subTerms = Generator.generateTermsWithIds(3);
        term.setSubTerms(subTerms.stream().map(TermInfo::new).collect(Collectors.toSet()));
        when(termService.findSubTerms(any())).thenReturn(subTerms);

        sut.findSubTerms(new ReadOnlyTerm(term));
        final ArgumentCaptor<Term> captor = ArgumentCaptor.forClass(Term.class);
        verify(termService).findSubTerms(captor.capture());
        final Term arg = captor.getValue();
        assertEquals(term.getUri(), arg.getUri());
        assertEquals(term.getVocabulary(), arg.getVocabulary());
    }
}
