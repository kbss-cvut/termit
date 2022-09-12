package cz.cvut.kbss.termit.persistence.snapshot;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.UnsupportedAssetOperationException;
import cz.cvut.kbss.termit.exception.UnsupportedOperationException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import cz.cvut.kbss.termit.persistence.dao.BaseDaoTestRunner;
import cz.cvut.kbss.termit.util.Utils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CascadingVocabularySnapshotRemoverTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private DescriptorFactory descriptorFactory;

    @Autowired
    private CascadingVocabularySnapshotRemover sut;

    @Test
    void removeSnapshotDeletesSpecifiedSnapshotRepositoryContext() {
        final URI vocabularyIri = Generator.generateUri();
        final Vocabulary snapshot = Generator.generateVocabularyWithId();
        snapshot.addType(cz.cvut.kbss.termit.util.Vocabulary.s_c_verze_slovniku);
        snapshot.setProperties(Collections.singletonMap(cz.cvut.kbss.termit.util.Vocabulary.s_p_je_verzi_slovniku,
                                                        Collections.singleton(vocabularyIri.toString())));
        transactional(() -> em.persist(snapshot, descriptorFactory.vocabularyDescriptor(snapshot)));
        final Snapshot toRemove = new Snapshot(snapshot.getUri(), Utils.timestamp(), vocabularyIri,
                                               cz.cvut.kbss.termit.util.Vocabulary.s_c_verze_slovniku);
        transactional(() -> sut.removeSnapshot(toRemove));
        verifyGraphEmpty(snapshot.getUri());
    }

    private void verifyGraphEmpty(URI graphUri) {
        assertFalse(em.createNativeQuery("ASK WHERE { GRAPH ?g { ?x ?y ?z . } }", Boolean.class)
                      .setParameter("g", graphUri)
                      .getSingleResult());
    }

    @Test
    void removeSnapshotDeletesSnapshotsRelatedToSpecifiedOne() {
        final URI vocabularyOneIri = Generator.generateUri();
        final Vocabulary snapshotOne = Generator.generateVocabularyWithId();
        snapshotOne.addType(cz.cvut.kbss.termit.util.Vocabulary.s_c_verze_slovniku);
        snapshotOne.setProperties(Collections.singletonMap(cz.cvut.kbss.termit.util.Vocabulary.s_p_je_verzi_slovniku,
                                                           Collections.singleton(vocabularyOneIri.toString())));
        final URI termOneIri = Generator.generateUri();
        final Term tSnapshotOne = Generator.generateTermWithId(snapshotOne.getUri());
        tSnapshotOne.addType(cz.cvut.kbss.termit.util.Vocabulary.s_c_verze_pojmu);
        tSnapshotOne.setProperties(Collections.singletonMap(cz.cvut.kbss.termit.util.Vocabulary.s_p_je_verzi_pojmu,
                                                            Collections.singleton(termOneIri.toString())));
        final URI vocabularyTwoIri = Generator.generateUri();
        final Vocabulary snapshotTwo = Generator.generateVocabularyWithId();
        snapshotTwo.addType(cz.cvut.kbss.termit.util.Vocabulary.s_c_verze_slovniku);
        snapshotTwo.setProperties(Collections.singletonMap(cz.cvut.kbss.termit.util.Vocabulary.s_p_je_verzi_slovniku,
                                                           Collections.singleton(vocabularyTwoIri.toString())));
        final URI termTwoIri = Generator.generateUri();
        final Term tSnapshotTwo = Generator.generateTermWithId(snapshotTwo.getUri());
        tSnapshotTwo.addType(cz.cvut.kbss.termit.util.Vocabulary.s_c_verze_pojmu);
        tSnapshotTwo.setProperties(Collections.singletonMap(cz.cvut.kbss.termit.util.Vocabulary.s_p_je_verzi_pojmu,
                                                            Collections.singleton(termTwoIri.toString())));

        transactional(() -> {
            em.persist(snapshotOne, descriptorFactory.vocabularyDescriptor(snapshotOne));
            em.persist(snapshotTwo, descriptorFactory.vocabularyDescriptor(snapshotTwo));
            tSnapshotOne.setGlossary(snapshotOne.getGlossary().getUri());
            tSnapshotTwo.setGlossary(snapshotTwo.getGlossary().getUri());
            em.persist(tSnapshotOne, descriptorFactory.termDescriptor(snapshotOne));
            em.persist(tSnapshotTwo, descriptorFactory.termDescriptor(snapshotTwo));
            Generator.addTermInVocabularyRelationship(tSnapshotOne, snapshotOne.getUri(), em);
            Generator.addTermInVocabularyRelationship(tSnapshotTwo, snapshotTwo.getUri(), em);
        });
        // Separate transaction to prevent IndividualAlreadyManagedException for tSnapshotTwo as Term and TermInfo in persistence context

        tSnapshotOne.addRelatedMatchTerm(new TermInfo(tSnapshotTwo));
        transactional(() -> em.merge(tSnapshotOne, descriptorFactory.termDescriptor(snapshotOne)));

        final Snapshot toRemove = new Snapshot(snapshotOne.getUri(), Utils.timestamp(), vocabularyOneIri,
                                               cz.cvut.kbss.termit.util.Vocabulary.s_c_verze_slovniku);
        transactional(() -> sut.removeSnapshot(toRemove));
        verifyGraphEmpty(snapshotOne.getUri());
        verifyGraphEmpty(snapshotTwo.getUri());
    }

    @Test
    void removeSnapshotThrowsUnsupportedOperationExceptionWhenAttemptingToRemoveNormalVocabulary() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary)));
        final Snapshot toRemove = new Snapshot(vocabulary.getUri(), Utils.timestamp(), null,
                                               cz.cvut.kbss.termit.util.Vocabulary.s_c_verze_slovniku);
        transactional(
                () -> assertThrows(UnsupportedOperationException.class, () -> sut.removeSnapshot(toRemove)));
    }

    @Test
    void removeSnapshotThrowsUnsupportedAssetOperationExceptionWhenProvidedSnapshotIsNotVocabulary() {
        final Snapshot toRemove = Generator.generateSnapshot(Generator.generateTermWithId());
        transactional(
                () -> assertThrows(UnsupportedAssetOperationException.class, () -> sut.removeSnapshot(toRemove)));
    }
}
