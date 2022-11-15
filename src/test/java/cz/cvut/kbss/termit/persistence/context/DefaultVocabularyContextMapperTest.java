package cz.cvut.kbss.termit.persistence.context;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.descriptors.EntityDescriptor;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.AmbiguousVocabularyContextException;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.dao.BaseDaoTestRunner;
import cz.cvut.kbss.termit.util.Utils;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DefaultVocabularyContextMapperTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    private DefaultVocabularyContextMapper sut;

    @BeforeEach
    void setUp() {
        this.sut = new DefaultVocabularyContextMapper(em);
    }

    @Test
    void getVocabularyContextResolvesVocabularyContextFromRepository() {
        final URI context = Generator.generateUri();
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(vocabulary, new EntityDescriptor(context)));

        assertEquals(context, sut.getVocabularyContext(vocabulary));
    }

    @Test
    void getVocabularyContextReturnsVocabularyUriWhenNoContextIsFoundInRepository() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        assertEquals(vocabulary.getUri(), sut.getVocabularyContext(vocabulary.getUri()));
    }

    @Test
    void getVocabularyContextThrowsAmbiguousVocabularyContextExceptionWhenMultipleContextsForVocabularyAreDetermined() {
        final Vocabulary v = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(v, new EntityDescriptor(v.getUri())));
        transactional(() -> em.persist(v, new EntityDescriptor(Generator.generateUri())));

        assertThrows(AmbiguousVocabularyContextException.class, () -> sut.getVocabularyContext(v));
    }

    @Test
    void getVocabularyContextReturnsCanonicalContextWhenAnotherInstanceIsBasedOnIt() {
        final Vocabulary v = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(v, new EntityDescriptor(v.getUri())));
        final URI workingVersionCtx = Generator.generateUri();
        transactional(() -> {
            em.persist(v, new EntityDescriptor(workingVersionCtx));
            generateCanonicalContextReference(workingVersionCtx, v.getUri(), em);
        });

        assertEquals(v.getUri(), sut.getVocabularyContext(v.getUri()));
    }

    static void generateCanonicalContextReference(URI context, URI canonical, EntityManager em) {
        final Repository repo = em.unwrap(Repository.class);
        try (final RepositoryConnection con = repo.getConnection()) {
            final ValueFactory vf = con.getValueFactory();
            con.add(vf.createIRI(context.toString()),
                    vf.createIRI(cz.cvut.kbss.termit.util.Vocabulary.s_p_vychazi_z_verze),
                    vf.createIRI(canonical.toString()), vf.createIRI(context.toString()));
        }
    }

    @Test
    void getVocabularyInContextReturnsIdentifierOfVocabularyStoredInSpecifiedContext() {
        final URI context = Generator.generateUri();
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(vocabulary, new EntityDescriptor(context)));

        final Optional<URI> result = sut.getVocabularyInContext(context);
        assertTrue(result.isPresent());
        assertEquals(vocabulary.getUri(), result.get());
    }

    @Test
    void getVocabularyContextReturnsEmptyOptionalWhenSpecifiedContextDoesNotExistOrDoesNotContainVocabulary() {
        final Optional<URI> result = sut.getVocabularyInContext(Generator.generateUri());
        assertNotNull(result);
        assertFalse(result.isPresent());
    }

    @Test
    void getVocabularyContextThrowsAmbiguousVocabularyContextExceptionWhenContextContainsMultipleVocabularies() {
        final URI context = Generator.generateUri();
        final Vocabulary vOne = Generator.generateVocabularyWithId();
        final Vocabulary vTwo = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(vOne, new EntityDescriptor(context)));
        transactional(() -> em.persist(vTwo, new EntityDescriptor(context)));

        assertThrows(AmbiguousVocabularyContextException.class, () -> sut.getVocabularyInContext(context));
    }

    @Test
    void getVocabularyContextsReturnsMapOfVocabulariesToContexts() {
        final List<Vocabulary> vocabularies = IntStream.range(0, 5).mapToObj(i -> Generator.generateVocabularyWithId())
                                                       .collect(Collectors.toList());
        final Map<URI, URI> vocToCtx = new HashMap<>();
        vocabularies.forEach(v -> vocToCtx.put(v.getUri(), Generator.generateUri()));
        transactional(() -> vocabularies.forEach(v -> em.persist(v, new EntityDescriptor(vocToCtx.get(v.getUri())))));

        final Map<URI, URI> result = sut.getVocabularyContexts();
        assertEquals(vocToCtx, result);
    }

    @Test
    void getVocabularyContextsExcludesSnapshots() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(vocabulary, new EntityDescriptor(vocabulary.getUri())));
        final Instant timestamp = Utils.timestamp();
        final String suffix = "/test-snapshot";
        transactional(() -> em.createNativeQuery(Utils.loadQuery("snapshot/vocabulary.ru"))
                              .setParameter("vocabulary", vocabulary)
                              .setParameter("suffix", suffix)
                              .setParameter("created", timestamp)
                              .executeUpdate());

        final Map<URI, URI> result = sut.getVocabularyContexts();
        assertEquals(1, result.size());
        assertEquals(vocabulary.getUri(), result.get(vocabulary.getUri()));
    }

    @Test
    void getVocabularyContextsExcludesWorkingCopiesOfVocabularies() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(vocabulary, new EntityDescriptor(vocabulary.getUri())));
        final Vocabulary workingCopy = Environment.cloneVocabulary(vocabulary);
        final URI workingCtx = Generator.generateUri();
        transactional(() -> {
            em.persist(workingCopy, new EntityDescriptor(workingCtx));
            Environment.insertContextBasedOnCanonical(workingCtx, vocabulary.getUri(), em);
        });

        final Map<URI, URI> result = sut.getVocabularyContexts();
        assertEquals(1, result.size());
        assertEquals(vocabulary.getUri(), result.get(vocabulary.getUri()));
    }
}
