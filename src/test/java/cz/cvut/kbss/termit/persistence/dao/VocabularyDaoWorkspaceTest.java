package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import cz.cvut.kbss.termit.workspace.EditableVocabularies;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class VocabularyDaoWorkspaceTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private DescriptorFactory descriptorFactory;

    @Autowired
    private EditableVocabularies editableVocabularies;

    @Autowired
    private VocabularyDao sut;

    private User author;

    @BeforeEach
    void setUp() {
        this.author = Generator.generateUserWithId();
        transactional(() -> em.persist(author));
        Environment.setCurrentUser(author);
    }

    @Test
    void findAllLoadsVocabulariesFromCorrectContexts() {
        final Vocabulary canonical = Generator.generateVocabularyWithId();
        final URI workingCtx = Generator.generateUri();
        final Vocabulary workingCopy = Environment.cloneVocabulary(canonical);
        workingCopy.setLabel("Working label");
        transactional(() -> em.persist(canonical, descriptorFactory.vocabularyDescriptor(canonical.getUri())));
        transactional(() -> em.persist(workingCopy, descriptorFactory.vocabularyDescriptor(workingCtx)));
        editableVocabularies.registerEditableVocabulary(workingCopy.getUri(), workingCtx);

        final List<Vocabulary> result = sut.findAll();
        assertEquals(Collections.singletonList(workingCopy), result);
        assertEquals(workingCopy.getLabel(), result.get(0).getLabel());
    }
}
