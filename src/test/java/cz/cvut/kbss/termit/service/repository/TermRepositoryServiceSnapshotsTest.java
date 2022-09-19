package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.dto.TermStatus;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.SnapshotNotEditableException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import cz.cvut.kbss.termit.service.BaseServiceTestRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class TermRepositoryServiceSnapshotsTest extends BaseServiceTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private DescriptorFactory descriptorFactory;

    @Autowired
    private TermRepositoryService sut;

    private UserAccount user;
    private Vocabulary vocabulary;

    @BeforeEach
    void setUp() {
        this.user = Generator.generateUserAccountWithPassword();
        transactional(() -> em.persist(user));
        Environment.setCurrentUser(user);

        this.vocabulary = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary)));
    }

    @Test
    void updateOfSnapshotThrowsSnapshotNotEditableException() {
        final Term snapshot = generateAndPersistSnapshot();

        snapshot.getLabel().set("de", "Apfelbaum, der");
        assertThrows(SnapshotNotEditableException.class, () -> sut.update(snapshot));
    }

    private Term generateAndPersistSnapshot() {
        final Term t = Generator.generateTermWithId();
        t.addType(cz.cvut.kbss.termit.util.Vocabulary.s_c_verze_pojmu);
        vocabulary.getGlossary().addRootTerm(t);
        t.setVocabulary(vocabulary.getUri());
        transactional(() -> {
            em.persist(t, descriptorFactory.termDescriptor(vocabulary));
            em.merge(vocabulary.getGlossary(), descriptorFactory.glossaryDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(t, vocabulary.getUri(), em);
        });
        return t;
    }

    @Test
    void setStatusOnSnapshotThrowsSnapshotNotEditableException() {
        final Term snapshot = generateAndPersistSnapshot();

        assertThrows(SnapshotNotEditableException.class, () -> sut.setStatus(snapshot, TermStatus.CONFIRMED));
    }

    @Test
    void addChildTermToSnapshotThrowsSnapshotNotEditableException() {
        final Term snapshot = generateAndPersistSnapshot();
        final Term child = Generator.generateTermWithId(vocabulary.getUri());

        assertThrows(SnapshotNotEditableException.class, () -> sut.addChildTerm(child, snapshot));
    }
}
