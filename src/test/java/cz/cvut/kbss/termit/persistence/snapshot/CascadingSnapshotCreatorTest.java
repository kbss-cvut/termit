package cz.cvut.kbss.termit.persistence.snapshot;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.DescriptorFactory;
import cz.cvut.kbss.termit.persistence.dao.BaseDaoTestRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        final Vocabulary root = generateVocabularyWithTerm();
        Vocabulary last = root;
        for (int i = 0; i < 5; i++) {
            final Vocabulary another = generateVocabularyWithTerm();
            addSkosRelationship(vocabularyTerms.get(last), vocabularyTerms.get(another));
            last = another;
        }

        transactional(() -> sut.createSnapshot(root));
        vocabularyTerms.keySet().forEach(v -> {
            assertTrue(em.createNativeQuery("ASK { ?snapshot ?isSnapshotOf ?v . }", Boolean.class)
                         .setParameter("isSnapshotOf",
                                       URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_je_verzi_slovniku))
                         .setParameter("v", v)
                         .getSingleResult());
        });
    }

    private Vocabulary generateVocabularyWithTerm() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        transactional(() -> {
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
}
