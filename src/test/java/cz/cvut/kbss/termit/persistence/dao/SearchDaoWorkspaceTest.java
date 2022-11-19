package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.termit.dto.FullTextSearchResult;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import cz.cvut.kbss.termit.util.Utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SearchDaoWorkspaceTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private DescriptorFactory descriptorFactory;

    @Autowired
    private SearchDao sut;

    private User user;

    @BeforeEach
    void setUp() {
        this.user = Generator.generateUserWithId();
        transactional(() -> em.persist(user));
        Environment.setCurrentUser(user);
    }

    @Test
    void defaultFullTextSearchFindsOnlyCanonicalTermsWithMatchingLabel() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final String searchString = "Matching";
        final Term matching = Generator.generateTermWithId(vocabulary.getUri());
        matching.setLabel(MultilingualString.create(searchString + " " + Utils.timestamp(), Environment.LANGUAGE));
        transactional(() -> {
            em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary));
            em.persist(matching, descriptorFactory.termDescriptorForSave(vocabulary.getUri()));
        });
        final URI workingCtx = Generator.generateUri();
        final Vocabulary workingVoc = Environment.cloneVocabulary(vocabulary);
        final Term workingTerm = Generator.generateTermWithId(workingVoc.getUri());
        workingTerm.setLabel(MultilingualString.create(searchString + " working " + Utils.timestamp(), Environment.LANGUAGE));
        transactional(() -> {
            em.persist(workingVoc, descriptorFactory.vocabularyDescriptor(workingCtx));
            em.persist(workingTerm, descriptorFactory.termDescriptor(workingCtx));
            Environment.insertContextBasedOnCanonical(workingCtx, vocabulary.getUri(), em);
        });

        final List<FullTextSearchResult> result = sut.fullTextSearch("matching");
        assertTrue(result.stream().anyMatch(item -> item.getUri().equals(matching.getUri())));
        assertTrue(result.stream().noneMatch(item -> item.getUri().equals(workingTerm.getUri())));
    }
}
