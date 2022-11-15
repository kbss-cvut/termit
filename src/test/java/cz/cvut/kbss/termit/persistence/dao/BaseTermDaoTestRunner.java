package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class BaseTermDaoTestRunner extends BaseDaoTestRunner {

    @Autowired
    EntityManager em;

    @Autowired
    DescriptorFactory descriptorFactory;

    @Autowired
    TermDao sut;

    Vocabulary vocabulary;

    void setUp() {
        this.vocabulary = Generator.generateVocabularyWithId();
        transactional(() -> em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary)));
    }
}
