package cz.cvut.kbss.termit.persistence.snapshot;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.persistence.DescriptorFactory;
import cz.cvut.kbss.termit.persistence.dao.BaseDaoTestRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.*;

class CascadingSnapshotCreatorTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private DescriptorFactory descriptorFactory;

    @Autowired
    private CascadingSnapshotCreator sut;

    private User author;

    private final Map<Vocabulary, Term> vocabularyTerms = new HashMap<>();

    @BeforeEach
    void setUp() {
        this.author = Generator.generateUserWithId();
        transactional(() -> em.persist(author));
        Environment.setCurrentUser(author);
    }

    @Test
    void createSnapshotRecursivelyIdentifiesVocabulariesToSnapshotViaTermSkosRelationships() {
        final Vocabulary root = generateVocabularyWithTerm(false);
        Vocabulary last = root;
        for (int i = 0; i < 5; i++) {
            final Vocabulary another = generateVocabularyWithTerm(false);
            addSkosRelationship(vocabularyTerms.get(last), vocabularyTerms.get(another));
            last = another;
        }

        transactional(() -> sut.createSnapshot(root));
        vocabularyTerms.keySet().forEach(
                v -> assertTrue(em.createNativeQuery("ASK { ?snapshot ?isSnapshotOf ?v . }", Boolean.class)
                                  .setParameter("isSnapshotOf",
                                                URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_je_verzi_slovniku))
                                  .setParameter("v", v)
                                  .getSingleResult()));
    }

    private Vocabulary generateVocabularyWithTerm(boolean rootTerm) {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        transactional(() -> {
            if (rootTerm) {
                vocabulary.getGlossary().addRootTerm(term);
            }
            em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
            term.setGlossary(vocabulary.getGlossary().getUri());
            em.persist(term, descriptorFactory.termDescriptor(term));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
        });
        vocabularyTerms.put(vocabulary, term);
        return vocabulary;
    }

    private void addSkosRelationship(Term termOne, Term termTwo) {
        final int i = Generator.randomInt(0, 3);
        switch (i) {
            case 0:
                termOne.addExactMatch(new TermInfo(termTwo));
                break;
            case 1:
                termOne.addRelatedMatchTerm(new TermInfo(termTwo));
                break;
            case 2:
                termOne.addParentTerm(termTwo);
                break;
        }
        transactional(() -> em.merge(termOne, descriptorFactory.termDescriptor(termOne)));
    }

    @Test
    void createSnapshotCreatesSnapshotOfVocabulary() {
        final Vocabulary vocabulary = generateVocabularyWithTerm(false);

        transactional(() -> {
            final Snapshot result = sut.createSnapshot(vocabulary);
            assertNotNull(result);
            assertEquals(vocabulary.getUri(), result.getVersionOf());
        });
        final Vocabulary result = findRequiredSnapshot(vocabulary);
        assertEquals(vocabulary.getLabel(), result.getLabel());
        assertEquals(vocabulary.getDescription(), result.getDescription());
        assertNotNull(result.getGlossary());
        assertNotNull(result.getModel());
    }

    private Vocabulary findRequiredSnapshot(Vocabulary vocabulary) {
        final Vocabulary result = em.createNativeQuery("SELECT ?s WHERE { ?s ?isSnapshotOf ?v }", Vocabulary.class)
                                    .setParameter("isSnapshotOf",
                                                  URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_je_verzi_slovniku))
                                    .setParameter("v", vocabulary)
                                    .getSingleResult();
        assertNotNull(result);
        return result;
    }

    @Test
    void createSnapshotEnsuresRootTermsAreAccessibleFromGlossarySnapshot() {
        final Vocabulary vocabulary = generateVocabularyWithTerm(true);

        transactional(() -> {
            final Snapshot result = sut.createSnapshot(vocabulary);
            assertNotNull(result);
            assertEquals(vocabulary.getUri(), result.getVersionOf());
        });
        final Vocabulary result = findRequiredSnapshot(vocabulary);
        assertEquals(1, result.getGlossary().getRootTerms().size());
    }

    @Test
    void createSnapshotCreatesVocabularyTermSnapshots() {
        final Vocabulary vocabulary = generateVocabularyWithTerm(true);
        final Term term = vocabularyTerms.get(vocabulary);

        transactional(() -> {
            final Snapshot result = sut.createSnapshot(vocabulary);
            assertNotNull(result);
            assertEquals(vocabulary.getUri(), result.getVersionOf());
        });
        final Vocabulary vocabularyResult = findRequiredSnapshot(vocabulary);
        final Term result = findRequiredSnapshot(term);
        assertEquals(term.getLabel(), result.getLabel());
        assertEquals(term.getDefinition(), result.getDefinition());
        assertEquals(term.getDescription(), result.getDescription());
        assertEquals(vocabularyResult.getGlossary().getUri(), result.getGlossary());
    }

    private Term findRequiredSnapshot(HasIdentifier term) {
        final Term result = em.createNativeQuery("SELECT ?s WHERE { ?s ?isSnapshotOf ?t }", Term.class)
                              .setParameter("isSnapshotOf",
                                            URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_je_verzi_pojmu))
                              .setParameter("t", term)
                              .getSingleResult();
        assertNotNull(result);
        return result;
    }

    @Test
    void createSnapshotEnsuresSnapshotsAreConnectedToAssetOriginals() {
        final Vocabulary vocabulary = generateVocabularyWithTerm(true);
        final Term term = vocabularyTerms.get(vocabulary);

        transactional(() -> sut.createSnapshot(vocabulary));
        final String queryString = "ASK { ?s ?isSnapshotOf ?a }";
        assertTrue(em.createNativeQuery(queryString, Boolean.class).setParameter("isSnapshotOf", URI.create(
                             cz.cvut.kbss.termit.util.Vocabulary.s_p_je_verzi_slovniku)).setParameter("a", vocabulary)
                     .getSingleResult());
        assertTrue(em.createNativeQuery(queryString, Boolean.class).setParameter("isSnapshotOf", URI.create(
                             cz.cvut.kbss.termit.util.Vocabulary.s_p_je_verzi_glosare)).setParameter("a", vocabulary.getGlossary())
                     .getSingleResult());
        assertTrue(em.createNativeQuery(queryString, Boolean.class).setParameter("isSnapshotOf", URI.create(
                             cz.cvut.kbss.termit.util.Vocabulary.s_p_je_verzi_modelu)).setParameter("a", vocabulary.getModel())
                     .getSingleResult());
        assertTrue(em.createNativeQuery(queryString, Boolean.class).setParameter("isSnapshotOf", URI.create(
                cz.cvut.kbss.termit.util.Vocabulary.s_p_je_verzi_pojmu)).setParameter("a", term).getSingleResult());
    }

    @Test
    void createSnapshotCreatesSnapshotsOfInterconnectedVocabulariesAndConnectsTheirTerms() {
        final Vocabulary root = generateVocabularyWithTerm(false);
        Vocabulary last = root;
        for (int i = 0; i < 5; i++) {
            final Vocabulary another = generateVocabularyWithTerm(false);
            addSkosRelationship(vocabularyTerms.get(last), vocabularyTerms.get(another));
            last = another;
        }

        transactional(() -> sut.createSnapshot(root));
        for (Term term : vocabularyTerms.values()) {
            final Term snapshot = findRequiredSnapshot(term);
            if (term.getExactMatchTerms() != null) {
                final TermInfo exactMatch = term.getExactMatchTerms().iterator().next();
                final Term exactMatchSnapshot = findRequiredSnapshot(exactMatch);
                assertThat(snapshot.getExactMatchTerms(), hasItem(new TermInfo(exactMatchSnapshot)));
            } else if (term.getRelatedMatch() != null) {
                final TermInfo relatedMatch = term.getRelatedMatch().iterator().next();
                final Term relatedMatchSnapshot = findRequiredSnapshot(relatedMatch);
                assertThat(snapshot.getRelatedMatch(), hasItem(new TermInfo(relatedMatchSnapshot)));
            } else if (term.getExternalParentTerms() != null) {
                final Term parent = term.getExternalParentTerms().iterator().next();
                final Term parentSnapshot = findRequiredSnapshot(parent);
                assertThat(snapshot.getExternalParentTerms(), hasItem(parentSnapshot));
            }
        }
    }
}
