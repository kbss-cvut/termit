package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import cz.cvut.kbss.termit.util.Utils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.*;

class SnapshotDaoTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private DescriptorFactory descriptorFactory;

    @Autowired
    private SnapshotDao sut;

    @Test
    void findRetrievesVocabularySnapshot() {
        enableRdfsInference(em);
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary)));
        final Instant timestamp = Utils.timestamp();
        final String suffix = "/test-snapshot";
        transactional(() -> em.createNativeQuery(Utils.loadQuery("snapshot/vocabulary.ru"))
                              .setParameter("vocabulary", vocabulary)
                              .setParameter("suffix", suffix)
                              .setParameter("created", timestamp)
                              .executeUpdate());

        final Optional<Snapshot> result = sut.find(URI.create(vocabulary.getUri().toString() + suffix));
        assertTrue(result.isPresent());
        assertEquals(vocabulary.getUri(), result.get().getVersionOf());
        assertThat(result.get().getTypes(), hasItem(cz.cvut.kbss.termit.util.Vocabulary.s_c_verze_slovniku));
    }

    @Test
    void findRetrievesTermSnapshot() {
        enableRdfsInference(em);
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        vocabulary.getGlossary().addRootTerm(term);
        transactional(() -> {
            em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
            term.setGlossary(vocabulary.getGlossary().getUri());
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
        });
        final Instant timestamp = Utils.timestamp();
        final String suffix = "/test-snapshot";
        transactional(() -> em.createNativeQuery(Utils.loadQuery("snapshot/term.ru"))
                              .setParameter("vocabulary", vocabulary)
                              .setParameter("suffix", suffix)
                              .setParameter("created", timestamp)
                              .executeUpdate());

        final Optional<Snapshot> result = sut.find(URI.create(term.getUri().toString() + suffix));
        assertTrue(result.isPresent());
        assertEquals(term.getUri(), result.get().getVersionOf());
        assertThat(result.get().getTypes(), hasItem(cz.cvut.kbss.termit.util.Vocabulary.s_c_verze_pojmu));
    }

    @Test
    void findReturnsEmptyOptionalWhenNoMatchingSnapshotExists() {
        enableRdfsInference(em);
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary)));
        final Optional<Snapshot> result = sut.find(vocabulary.getUri());
        assertFalse(result.isPresent());
    }
}
