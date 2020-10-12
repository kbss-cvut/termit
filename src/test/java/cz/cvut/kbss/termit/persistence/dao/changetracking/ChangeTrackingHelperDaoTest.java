package cz.cvut.kbss.termit.persistence.dao.changetracking;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.descriptors.EntityDescriptor;
import cz.cvut.kbss.jopa.model.descriptors.FieldDescriptor;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.DescriptorFactory;
import cz.cvut.kbss.termit.persistence.dao.BaseDaoTestRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class ChangeTrackingHelperDaoTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private DescriptorFactory descriptorFactory;

    @Autowired
    private ChangeTrackingHelperDao sut;

    private User author;

    @BeforeEach
    void setUp() {
        this.author = Generator.generateUserWithId();
        transactional(() -> em.persist(author));
        Environment.setCurrentUser(author);
    }

    @Test
    void findStoredRetrievesRepositoryInstanceOfSpecifiedAsset() {
        final Vocabulary voc = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(voc, descriptorFactory.vocabularyDescriptor(voc)));

        final Vocabulary result = sut.findStored(voc);
        assertNotNull(result);
        assertEquals(voc.getUri(), result.getUri());
    }

    @Test
    void findStoredThrowsNotFoundExceptionWhenStoredInstanceIsNotFound() {
        final Vocabulary voc = Generator.generateVocabularyWithId();
        assertThrows(NotFoundException.class, () -> sut.findStored(voc));
    }

    @Test
    void findStoredDetachesRetrievedInstanceFromPersistenceContext() {
        final Vocabulary voc = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(voc, descriptorFactory.vocabularyDescriptor(voc)));

        transactional(() -> {
            final Vocabulary result = sut.findStored(voc);
            assertNotNull(result);
            assertFalse(em.contains(result));
        });
    }

    @Test
    void findStoredHandlesTermExistingInMultipleWorkspaces() {
        final Vocabulary voc = Generator.generateVocabularyWithId();
        final Term term = Generator.generateTermWithId();
        term.setGlossary(voc.getGlossary().getUri());
        voc.getGlossary().addRootTerm(term);
        term.setVocabulary(voc.getUri());
        transactional(() -> {
            em.persist(voc, descriptorFactory.vocabularyDescriptor(voc));
            em.persist(term, descriptorFactory.termDescriptor(term));
            Generator.addTermInVocabularyRelationship(term, voc.getUri(), em);
        });
        addTermToVocabularyInAnotherWorkspace(term);

        final Term result = sut.findStored(term);
        assertNotNull(result);
        assertEquals(term, result);
        assertEquals(term.getLabel(), result.getLabel());
    }

    private void addTermToVocabularyInAnotherWorkspace(Term term) {
        final Vocabulary anotherWorkspaceVocabulary = Generator.generateVocabulary();
        anotherWorkspaceVocabulary.setUri(term.getVocabulary());
        final URI anotherWorkspaceCtx = Generator.generateUri();
        final Term copy = new Term();
        copy.setUri(term.getUri());
        copy.setLabel("Different label");

        transactional(() -> {
            em.persist(anotherWorkspaceVocabulary, new EntityDescriptor(anotherWorkspaceCtx));
            copy.setGlossary(term.getGlossary());
            final EntityDescriptor termDescriptor = new EntityDescriptor(anotherWorkspaceCtx);
            termDescriptor.addAttributeContext(descriptorFactory.fieldSpec(Term.class, "parentTerms"), null);
            termDescriptor.addAttributeDescriptor(descriptorFactory.fieldSpec(Term.class, "vocabulary"),
                    new FieldDescriptor((URI) null, descriptorFactory.fieldSpec(Term.class, "vocabulary")));
            em.persist(copy, termDescriptor);
        });
    }
}
