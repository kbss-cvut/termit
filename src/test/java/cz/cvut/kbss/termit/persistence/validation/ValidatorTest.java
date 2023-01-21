package cz.cvut.kbss.termit.persistence.validation;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.validation.ValidationResult;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import cz.cvut.kbss.termit.persistence.dao.BaseDaoTestRunner;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidatorTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private DescriptorFactory descriptorFactory;

    @Autowired
    private Configuration config;

    @BeforeEach
    void setUp() {
        final User author = Generator.generateUserWithId();
        transactional(() -> em.persist(author));
        Environment.setCurrentUser(author);
    }

    @Test
    void validateUsesOverrideRulesToAllowI18n() {
        final Vocabulary vocabulary = generateVocabulary();
        transactional(() -> {
            final Validator sut = new Validator(em, config);
            final List<ValidationResult> result = sut.validate(Collections.singleton(vocabulary.getUri()));
            assertTrue(result.stream().noneMatch(
                    vr -> vr.getMessage().get("en").contains("The term does not have a preferred label in Czech")));
            assertTrue(result.stream().noneMatch(
                    vr -> vr.getMessage().get("en").contains("The term does not have a definition in Czech")));
            assertTrue(result.stream().anyMatch(vr -> vr.getMessage().get("en").contains(
                    "The term does not have a preferred label in the primary configured language of this deployment of TermIt")));
        });
    }

    private Vocabulary generateVocabulary() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        term.getLabel().remove(Constants.DEFAULT_LANGUAGE);
        term.getLabel().set("de", "Apfelbaum, der");
        vocabulary.getGlossary().addRootTerm(term);
        transactional(() -> {
            em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
        });
        return vocabulary;
    }
}
