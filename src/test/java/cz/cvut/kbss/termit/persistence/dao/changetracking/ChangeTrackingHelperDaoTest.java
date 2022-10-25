package cz.cvut.kbss.termit.persistence.dao.changetracking;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import cz.cvut.kbss.termit.persistence.dao.BaseDaoTestRunner;
import cz.cvut.kbss.termit.workspace.EditableVocabularies;
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
    private EditableVocabularies editableVocabularies;

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
    void findStoredSupportsWorkingWithWorkspaces() {
        final Vocabulary voc = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(voc, descriptorFactory.vocabularyDescriptor(voc)));
        final Vocabulary workingCopy = Environment.cloneVocabulary(voc);
        workingCopy.setLabel("Working label");
        final URI workingCtx = Generator.generateUri();
        editableVocabularies.registerEditableVocabulary(voc.getUri(), workingCtx);
        transactional(() -> em.persist(workingCopy, descriptorFactory.vocabularyDescriptor(workingCtx)));

        final Vocabulary result = sut.findStored(workingCopy);
        assertNotNull(result);
        assertEquals(workingCopy.getLabel(), result.getLabel());
    }
}
