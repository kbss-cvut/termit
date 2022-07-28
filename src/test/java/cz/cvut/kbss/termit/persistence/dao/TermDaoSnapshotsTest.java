package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.dto.listing.TermDto;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.util.Constants;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static cz.cvut.kbss.termit.environment.util.ContainsSameEntities.containsSameEntities;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.*;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class TermDaoSnapshotsTest extends BaseTermDaoTestRunner {

    @BeforeEach
    void setUp() {
        super.setUp();
    }

    @Test
    void findSnapshotsRetrievesSnapshotsOfSpecifiedTermOrderedByCreationDateDescending() {
        enableRdfsInference(em);
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        transactional(() -> em.persist(term, descriptorFactory.termDescriptor(vocabulary)));
        final List<Term> snapshots = IntStream.range(0, 5).mapToObj(i -> {
            final Instant timestamp = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(i * 2L);
            return generateSnapshotStub(term, timestamp);
        }).collect(Collectors.toList());

        final List<Snapshot> result = sut.findSnapshots(term);
        assertEquals(result.size(), snapshots.size());
        assertThat(result, containsSameEntities(snapshots));
    }

    private Term generateSnapshotStub(Term term, Instant timestamp) {
        return generateSnapshotStub(term, timestamp, true);
    }

    private Term generateSnapshotStub(Term term, Instant timestamp, boolean snapshotVocabulary) {
        final String strTimestamp = timestamp.toString().replace(":", "");
        final Term stub = new Term();
        stub.setUri(URI.create(term.getUri().toString() + "/version/" + strTimestamp));
        stub.setLabel(new MultilingualString(term.getLabel().getValue()));
        stub.setDefinition(new MultilingualString(term.getDefinition().getValue()));
        stub.setDescription(new MultilingualString(term.getDescription().getValue()));
        // This one will simulate a vocabulary snapshot
        final URI vocSnapshotUri = URI.create(vocabulary.getUri().toString() + "/version/" + strTimestamp);
        stub.setVocabulary(vocSnapshotUri);
        stub.setProperties(new HashMap<>());
        if (snapshotVocabulary) {
            transactional(() -> {
                final Vocabulary vocabularySnapshot = Generator.generateVocabulary();
                vocabularySnapshot.setUri(vocSnapshotUri);
                vocabularySnapshot.getGlossary().addRootTerm(stub);
                em.persist(vocabularySnapshot, descriptorFactory.vocabularyDescriptor(vocSnapshotUri));
                stub.setGlossary(vocabularySnapshot.getGlossary().getUri());
            });
        }
        transactional(() -> {
            em.persist(stub, descriptorFactory.termDescriptor(vocSnapshotUri));
            final Repository repo = em.unwrap(Repository.class);
            try (final RepositoryConnection connection = repo.getConnection()) {
                final ValueFactory vf = connection.getValueFactory();
                final IRI stubIri = vf.createIRI(stub.getUri().toString());
                connection.begin();
                connection.add(stubIri, vf.createIRI(cz.cvut.kbss.termit.util.Vocabulary.s_p_je_verzi_pojmu),
                        vf.createIRI(term.getUri().toString()), vf.createIRI(vocSnapshotUri.toString()));
                connection.add(stubIri,
                        vf.createIRI(cz.cvut.kbss.termit.util.Vocabulary.s_p_ma_datum_a_cas_vytvoreni_verze),
                        vf.createLiteral(Date.from(timestamp)), vf.createIRI(vocSnapshotUri.toString()));
                connection.add(stubIri, RDF.TYPE, vf.createIRI(cz.cvut.kbss.termit.util.Vocabulary.s_c_verze_pojmu),
                        vf.createIRI(vocSnapshotUri.toString()));
                connection.commit();
            }
        });
        return stub;
    }

    @Test
    void findVersionValidAtReturnsLatestSnapshotCreatedBeforeSpecifiedInstant() {
        enableRdfsInference(em);
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        transactional(() -> em.persist(term, descriptorFactory.termDescriptor(term)));
        Term expected = null;
        Instant validAt = null;
        Instant timestamp;
        for (int i = 0; i < 5; i++) {
            timestamp = Instant.now().truncatedTo(ChronoUnit.SECONDS).minus((i + 1) * 2L, ChronoUnit.DAYS);
            final Term v = generateSnapshotStub(term, timestamp);
            if (i == 3) {
                expected = v;
                validAt = timestamp.plus(1, ChronoUnit.HOURS);
            }
        }

        final Optional<Term> result = sut.findVersionValidAt(term, validAt);
        assertTrue(result.isPresent());
        assertEquals(expected, result.get());
    }

    @Test
    void findVersionValidAtReturnsNullWhenNoSnapshotAtSpecifiedInstantExists() {
        enableRdfsInference(em);
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        transactional(() -> em.persist(term, descriptorFactory.termDescriptor(term)));
        IntStream.range(0, 5).forEach(i -> {
            final Instant timestamp = Instant.now().truncatedTo(ChronoUnit.SECONDS)
                                             .minus((i + 1) * 2L, ChronoUnit.HOURS);
            generateSnapshotStub(term, timestamp);
        });

        final Optional<Term> result = sut.findVersionValidAt(term, Instant.now().minus(1, ChronoUnit.DAYS));
        assertNotNull(result);
        assertFalse(result.isPresent());
    }

    @Test
    void findAllFromVocabularyDoesNotIncludeSnapshotsInResult() {
        enableRdfsInference(em);
        final Term term = generateTermWithSnapshot();

        final List<TermDto> result = sut.findAll(vocabulary);
        assertEquals(Collections.singletonList(new TermDto(term)), result);
    }

    private Term generateTermWithSnapshot() {
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        transactional(() -> {
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            vocabulary.getGlossary().addRootTerm(term);
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
        });
        generateSnapshotStub(term, Instant.now());
        return term;
    }

    @Test
    void findAllRootsDoesNotIncludeSnapshotsInResult() {
        enableRdfsInference(em);
        final Term term = generateTermWithSnapshot();

        final List<TermDto> result = sut.findAllRoots(vocabulary, Constants.DEFAULT_PAGE_SPEC, Collections.emptySet());
        assertEquals(Collections.singletonList(new TermDto(term)), result);
    }

    @Test
    void findAllRootsWithIncludedDoesNotIncludeSnapshotsInResult() {
        enableRdfsInference(em);
        final Term term = generateTermWithSnapshot();

        final List<TermDto> result = sut.findAllRoots(Constants.DEFAULT_PAGE_SPEC, Collections.emptySet());
        assertEquals(Collections.singletonList(new TermDto(term)), result);
    }

    @Test
    void findAllRootsIncludingImportsDoesNotIncludeSnapshotsInResult() {
        enableRdfsInference(em);
        final Term term = generateTermWithSnapshot();

        final List<TermDto> result = sut.findAllRootsIncludingImports(vocabulary, Constants.DEFAULT_PAGE_SPEC,
                Collections.emptySet());
        assertEquals(Collections.singletonList(new TermDto(term)), result);
    }

    @Test
    void findAllBySearchStringDoesNotIncludeSnapshotsInResult() {
        enableRdfsInference(em);
        final Term term = generateTermWithSnapshot();

        final List<TermDto> result = sut.findAll(term.getPrimaryLabel());
        assertEquals(Collections.singletonList(new TermDto(term)), result);
    }

    @Test
    void findVersionValidAtLoadsInferredRelationshipsForFoundSnapshot() {
        enableRdfsInference(em);
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        final Term related = Generator.generateTermWithId(vocabulary.getUri());
        transactional(() -> {
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            em.persist(related, descriptorFactory.termDescriptor(vocabulary));
        });
        transactional(() -> {
            term.addRelatedTerm(new TermInfo(related));
            em.merge(term, descriptorFactory.termDescriptor(term));
            Generator.simulateInferredSkosRelationship(term, Collections.singleton(related), SKOS.RELATED, em);
        });
        final Instant timestamp = Instant.now().truncatedTo(ChronoUnit.SECONDS).minus(1, ChronoUnit.DAYS);
        final Term termSnapshot = generateSnapshotStub(term, timestamp);
        final Term relatedSnapshot = generateSnapshotStub(related, timestamp, false);
        transactional(() -> {
            final Term updateSnapshot = em.find(Term.class, termSnapshot.getUri());
            updateSnapshot.addRelatedTerm(new TermInfo(relatedSnapshot));
            em.merge(updateSnapshot, descriptorFactory.termDescriptor(updateSnapshot));
        });

        final Optional<Term> termSnapshotResult = sut.findVersionValidAt(term, Instant.now());
        final Optional<Term> relatedSnapshotResult = sut.findVersionValidAt(related, Instant.now());
        assertTrue(termSnapshotResult.isPresent());
        assertTrue(relatedSnapshotResult.isPresent());

        assertThat(termSnapshotResult.get().getRelated(), hasItem(new TermInfo(relatedSnapshot)));
        assertThat(relatedSnapshotResult.get().getInverseRelated(), hasItem(new TermInfo(termSnapshot)));
    }
}
