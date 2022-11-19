package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;

public class TermDaoExactMatchTermsTest extends BaseTermDaoTestRunner {

    @BeforeEach
    void setUp() {
        super.setUp();
    }

    @Test
    void findLoadsInferredInverseExactMatchTerms() {
        final Vocabulary matchVocabulary = Generator.generateVocabularyWithId();
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        final List<Term> exactMatchTerms = Arrays.asList(Generator.generateTermWithId(matchVocabulary.getUri()),
                                                         Generator.generateTermWithId(matchVocabulary.getUri()));
        transactional(() -> {
            em.persist(term, descriptorFactory.termDescriptorForSave(term));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
            em.persist(matchVocabulary, descriptorFactory.vocabularyDescriptor(matchVocabulary));
            exactMatchTerms.forEach(t -> {
                em.persist(t, descriptorFactory.termDescriptorForSave(t));
                Generator.addTermInVocabularyRelationship(t, t.getVocabulary(), em);
            });
        });
        transactional(() -> generateExactMatchRelationships(term, exactMatchTerms));

        final Optional<Term> result = sut.find(term.getUri());
        assertTrue(result.isPresent());
        assertEquals(exactMatchTerms.size(), result.get().getInverseExactMatchTerms().size());
        exactMatchTerms.forEach(rt -> assertThat(result.get().getInverseExactMatchTerms(), hasItem(new TermInfo(rt))));
    }

    private void generateExactMatchRelationships(Term term, Collection<Term> related) {
        Generator.simulateInferredSkosInverseRelationship(term, related, SKOS.EXACT_MATCH, em);
    }

    @Test
    void loadingInferredInverseExactMatchExcludesExactMatchAssertedFromSubject() {
        final Vocabulary matchVocabulary = Generator.generateVocabularyWithId();
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        final List<Term> exactMatchTerms = Arrays.asList(Generator.generateTermWithId(matchVocabulary.getUri()),
                                                         Generator.generateTermWithId(matchVocabulary.getUri()));
        final List<Term> inverseExactMatchTerms = new ArrayList<>(
                Collections.singletonList(Generator.generateTermWithId(matchVocabulary.getUri())));
        inverseExactMatchTerms.addAll(exactMatchTerms);
        transactional(() -> {
            em.persist(term, descriptorFactory.termDescriptorForSave(vocabulary.getUri()));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
            em.persist(matchVocabulary, descriptorFactory.vocabularyDescriptor(matchVocabulary));
            inverseExactMatchTerms.forEach(t -> {
                em.persist(t, descriptorFactory.termDescriptorForSave(t.getVocabulary()));
                Generator.addTermInVocabularyRelationship(t, t.getVocabulary(), em);
            });
            generateExactMatchRelationships(term, inverseExactMatchTerms);
        });
        term.setExactMatchTerms(exactMatchTerms.stream().map(TermInfo::new).collect(Collectors.toSet()));
        transactional(() -> em.merge(term, descriptorFactory.termDescriptorForSave(term)));

        final Optional<Term> result = sut.find(term.getUri());
        assertTrue(result.isPresent());
        assertFalse(result.get().getInverseExactMatchTerms().isEmpty());
        exactMatchTerms.forEach(
                r -> assertThat(result.get().getInverseExactMatchTerms(), not(hasItem(new TermInfo(r)))));
    }
}
