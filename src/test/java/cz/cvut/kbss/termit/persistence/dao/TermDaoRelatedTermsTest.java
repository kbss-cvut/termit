package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.util.Utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

public class TermDaoRelatedTermsTest extends BaseTermDaoTestRunner {

    @BeforeEach
    void setUp() {
        super.setUp();
    }

    @Test
    void findLoadsInferredInverseRelatedTerms() {
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        final List<Term> related = Arrays.asList(Generator.generateTermWithId(vocabulary.getUri()),
                                                 Generator.generateTermWithId(vocabulary.getUri()),
                                                 Generator.generateTermWithId(vocabulary.getUri()));
        transactional(() -> {
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
            related.forEach(t -> {
                em.persist(t, descriptorFactory.termDescriptor(vocabulary));
                Generator.addTermInVocabularyRelationship(t, vocabulary.getUri(), em);
            });
            generateRelatedRelationships(term, related, SKOS.RELATED);
        });

        final Optional<Term> result = sut.find(term.getUri());
        assertTrue(result.isPresent());
        assertEquals(related.size(), result.get().getInverseRelated().size());
        related.forEach(rt -> assertThat(result.get().getInverseRelated(), hasItem(new TermInfo(rt))));
    }

    private void generateRelatedRelationships(Term term, Collection<Term> related, String relationship) {
        Generator.simulateInferredSkosInverseRelationship(term, related, relationship, em);
    }

    @Test
    void loadingInferredInverseRelatedExcludesRelatedAssertedFromSubject() {
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        final List<Term> related = Arrays.asList(Generator.generateTermWithId(vocabulary.getUri()),
                                                 Generator.generateTermWithId(vocabulary.getUri()));
        final List<Term> inverseRelated = new ArrayList<>(
                Arrays.asList(Generator.generateTermWithId(vocabulary.getUri()),
                              Generator.generateTermWithId(vocabulary.getUri())));
        inverseRelated.addAll(related);
        transactional(() -> {
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
            inverseRelated.forEach(t -> {
                em.persist(t, descriptorFactory.termDescriptor(vocabulary));
                Generator.addTermInVocabularyRelationship(t, vocabulary.getUri(), em);
            });
            generateRelatedRelationships(term, inverseRelated, SKOS.RELATED);
        });
        term.setRelated(related.stream().map(TermInfo::new).collect(Collectors.toSet()));
        transactional(() -> em.merge(term, descriptorFactory.termDescriptor(term)));

        final Optional<Term> result = sut.find(term.getUri());
        assertTrue(result.isPresent());
        assertFalse(result.get().getInverseRelated().isEmpty());
        related.forEach(r -> assertThat(result.get().getInverseRelated(), not(hasItem(new TermInfo(r)))));
    }

    @Test
    void findLoadsInferredInverseRelatedMatchTerms() {
        final Vocabulary matchVocabulary = Generator.generateVocabularyWithId();
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        final List<Term> relatedMatch = Arrays.asList(Generator.generateTermWithId(matchVocabulary.getUri()),
                                                      Generator.generateTermWithId(matchVocabulary.getUri()));
        transactional(() -> {
            em.persist(term, descriptorFactory.termDescriptorForSave(vocabulary.getUri()));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
            em.persist(matchVocabulary, descriptorFactory.vocabularyDescriptor(matchVocabulary));
            relatedMatch.forEach(t -> {
                em.persist(t, descriptorFactory.termDescriptorForSave(t));
                Generator.addTermInVocabularyRelationship(t, t.getVocabulary(), em);
            });
            generateRelatedRelationships(term, relatedMatch, SKOS.RELATED_MATCH);
        });

        final Optional<Term> result = sut.find(term.getUri());
        assertTrue(result.isPresent());
        assertEquals(relatedMatch.size(), result.get().getInverseRelatedMatch().size());
        relatedMatch.forEach(rt -> assertThat(result.get().getInverseRelatedMatch(), hasItem(new TermInfo(rt))));
    }

    @Test
    void loadingInferredInverseRelatedMatchExcludesRelatedMatchAssertedFromSubject() {
        final Vocabulary matchVocabulary = Generator.generateVocabularyWithId();
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        final List<Term> relatedMatch = Arrays.asList(Generator.generateTermWithId(matchVocabulary.getUri()),
                                                      Generator.generateTermWithId(matchVocabulary.getUri()));
        final List<Term> inverseRelatedMatch = new ArrayList<>(
                Collections.singletonList(Generator.generateTermWithId(matchVocabulary.getUri())));
        inverseRelatedMatch.addAll(relatedMatch);
        transactional(() -> {
            em.persist(term, descriptorFactory.termDescriptorForSave(vocabulary.getUri()));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
            em.persist(matchVocabulary, descriptorFactory.vocabularyDescriptor(matchVocabulary));
            inverseRelatedMatch.forEach(t -> {
                em.persist(t, descriptorFactory.termDescriptorForSave(t.getVocabulary()));
                Generator.addTermInVocabularyRelationship(t, t.getVocabulary(), em);
            });
            generateRelatedRelationships(term, inverseRelatedMatch, SKOS.RELATED_MATCH);
        });
        term.setRelatedMatch(relatedMatch.stream().map(TermInfo::new).collect(Collectors.toSet()));
        transactional(() -> em.merge(term, descriptorFactory.termDescriptorForSave(term)));

        final Optional<Term> result = sut.find(term.getUri());
        assertTrue(result.isPresent());
        assertFalse(result.get().getInverseRelatedMatch().isEmpty());
        relatedMatch.forEach(r -> assertThat(result.get().getInverseRelatedMatch(), not(hasItem(new TermInfo(r)))));
    }

    @Test
    void findAllLoadsInferredInverseRelatedAndRelatedMatchTerms() {
        final Vocabulary matchVocabulary = Generator.generateVocabularyWithId();
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        final List<Term> related = Arrays.asList(Generator.generateTermWithId(vocabulary.getUri()), Generator
                .generateTermWithId(vocabulary.getUri()));
        final List<Term> inverseRelated = new ArrayList<>(Arrays
                                                                  .asList(Generator.generateTermWithId(
                                                                          vocabulary.getUri()), Generator
                                                                                  .generateTermWithId(
                                                                                          vocabulary.getUri())));
        final List<Term> relatedMatch = Arrays.asList(Generator.generateTermWithId(Generator.generateUri()), Generator
                .generateTermWithId(Generator.generateUri()));
        final List<Term> inverseRelatedMatch = new ArrayList<>(Collections.singletonList(Generator.generateTermWithId(matchVocabulary.getUri())));
        final Collection<Term> allRelated = Utils.joinCollections(related, inverseRelated);
        final Collection<Term> allRelatedMatch = Utils.joinCollections(relatedMatch, inverseRelatedMatch);
        transactional(() -> {
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
            allRelated.forEach(t -> {
                em.persist(t, descriptorFactory.termDescriptor(vocabulary));
                Generator.addTermInVocabularyRelationship(t, vocabulary.getUri(), em);
            });
            em.persist(matchVocabulary, descriptorFactory.vocabularyDescriptor(matchVocabulary));
            generateRelatedRelationships(term, allRelated, SKOS.RELATED);
            allRelatedMatch.forEach(t -> {
                em.persist(t, descriptorFactory.termDescriptor(t.getVocabulary()));
                Generator.addTermInVocabularyRelationship(t, t.getVocabulary(), em);
            });
            generateRelatedRelationships(term, allRelatedMatch, SKOS.RELATED_MATCH);
        });
        term.setRelated(related.stream().map(TermInfo::new).collect(Collectors.toSet()));
        term.setRelatedMatch(relatedMatch.stream().map(TermInfo::new).collect(Collectors.toSet()));
        transactional(() -> em.merge(term, descriptorFactory.termDescriptorForSave(term)));

        final List<Term> result = sut.findAllFull(vocabulary);
        final Optional<Term> singleResult = result.stream().filter(t -> t.equals(term)).findFirst();
        assertTrue(singleResult.isPresent());
        inverseRelated.forEach(ir -> assertThat(singleResult.get().getInverseRelated(), hasItem(new TermInfo(ir))));
        inverseRelatedMatch
                .forEach(ir -> assertThat(singleResult.get().getInverseRelatedMatch(), hasItem(new TermInfo(ir))));
    }

    @Test
    void inferredInverseRelatedDoesNotContainRelatedMatch() {
        final Vocabulary matchVocabulary = Generator.generateVocabularyWithId();
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        final List<Term> relatedMatch = Arrays.asList(Generator.generateTermWithId(matchVocabulary.getUri()), Generator
                .generateTermWithId(matchVocabulary.getUri()));
        transactional(() -> {
            em.persist(term, descriptorFactory.termDescriptorForSave(term));
            em.persist(matchVocabulary, descriptorFactory.vocabularyDescriptor(matchVocabulary));
            relatedMatch.forEach(t -> em.persist(t, descriptorFactory.termDescriptorForSave(t)));
            generateRelatedRelationships(term, relatedMatch, SKOS.RELATED);
        });
        term.setRelatedMatch(relatedMatch.stream().map(TermInfo::new).collect(Collectors.toSet()));
        transactional(() -> em.merge(term, descriptorFactory.termDescriptorForSave(term)));

        final Optional<Term> result = sut.find(term.getUri());
        assertTrue(result.isPresent());
        assertThat(result.get().getInverseRelated(), anyOf(emptyCollectionOf(TermInfo.class), nullValue()));
        assertThat(result.get().getRelatedMatch(), hasItems(relatedMatch.stream().map(TermInfo::new)
                                                                        .toArray(TermInfo[]::new)));
    }
}
