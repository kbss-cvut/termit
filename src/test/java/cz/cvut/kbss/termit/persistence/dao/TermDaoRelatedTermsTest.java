package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.DescriptorFactory;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;

public class TermDaoRelatedTermsTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private DescriptorFactory descriptorFactory;

    @Autowired
    private TermDao sut;

    private Vocabulary vocabulary;

    @BeforeEach
    void setUp() {
        this.vocabulary = Generator.generateVocabulary();
        vocabulary.setUri(Generator.generateUri());
        transactional(() -> em.persist(vocabulary, descriptorFactory.vocabularyDescriptor(vocabulary)));
    }

    @Test
    void findLoadsInferredInverseRelatedTerms() {
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        final List<Term> related = Arrays.asList(Generator.generateTermWithId(vocabulary.getUri()), Generator.generateTermWithId(vocabulary.getUri()), Generator.generateTermWithId(vocabulary.getUri()));
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
        final Repository repo = em.unwrap(Repository.class);
        try (final RepositoryConnection conn = repo.getConnection()) {
            final ValueFactory vf = conn.getValueFactory();
            conn.begin();
            for (Term r : related) {
                // Don't put it into any specific context to make it look like inference
                conn.add(vf.createIRI(r.getUri().toString()), vf.createIRI(relationship), vf.createIRI(term.getUri().toString()));
            }
            conn.commit();
        }
    }

    @Test
    void loadingInferredInverseRelatedExcludesRelatedAssertedFromSubject() {
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        final List<Term> related = Arrays.asList(Generator.generateTermWithId(vocabulary.getUri()), Generator.generateTermWithId(vocabulary.getUri()));
        final List<Term> inverseRelated = new ArrayList<>(Arrays.asList(Generator.generateTermWithId(vocabulary.getUri()), Generator.generateTermWithId(vocabulary.getUri())));
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
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        final List<Term> relatedMatch = Arrays.asList(Generator.generateTermWithId(Generator.generateUri()), Generator.generateTermWithId(Generator.generateUri()));
        transactional(() -> {
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
            relatedMatch.forEach(t -> {
                em.persist(t, descriptorFactory.termDescriptor(t));
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
        final Term term = Generator.generateTermWithId(vocabulary.getUri());
        final List<Term> relatedMatch = Arrays.asList(Generator.generateTermWithId(Generator.generateUri()), Generator.generateTermWithId(Generator.generateUri()));
        final List<Term> inverseRelatedMatch = new ArrayList<>(Collections.singletonList(Generator.generateTermWithId(Generator.generateUri())));
        inverseRelatedMatch.addAll(relatedMatch);
        transactional(() -> {
            em.persist(term, descriptorFactory.termDescriptor(vocabulary));
            Generator.addTermInVocabularyRelationship(term, vocabulary.getUri(), em);
            inverseRelatedMatch.forEach(t -> {
                em.persist(t, descriptorFactory.termDescriptor(t.getVocabulary()));
                Generator.addTermInVocabularyRelationship(t, t.getVocabulary(), em);
            });
            generateRelatedRelationships(term, inverseRelatedMatch, SKOS.RELATED_MATCH);
        });
        term.setRelatedMatch(relatedMatch.stream().map(TermInfo::new).collect(Collectors.toSet()));
        transactional(() -> em.merge(term, descriptorFactory.termDescriptor(term)));

        final Optional<Term> result = sut.find(term.getUri());
        assertTrue(result.isPresent());
        assertFalse(result.get().getInverseRelatedMatch().isEmpty());
        relatedMatch.forEach(r -> assertThat(result.get().getInverseRelatedMatch(), not(hasItem(new TermInfo(r)))));
    }
}
