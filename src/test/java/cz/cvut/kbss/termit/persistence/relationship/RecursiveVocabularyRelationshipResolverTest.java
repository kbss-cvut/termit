package cz.cvut.kbss.termit.persistence.relationship;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.dto.TermInfo;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import cz.cvut.kbss.termit.persistence.dao.BaseDaoTestRunner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecursiveVocabularyRelationshipResolverTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private DescriptorFactory descriptorFactory;

    @Autowired
    private RecursiveVocabularyRelationshipResolver sut;

    @Test
    void getRelatedVocabulariesResolvesIndirectlyRelatedVocabulariesViaSkosMappingProperties() {
        final Vocabulary subjectVocabulary = Generator.generateVocabularyWithId();
        final Vocabulary intermediateVocabulary = Generator.generateVocabularyWithId();
        final Vocabulary targetVocabulary = Generator.generateVocabularyWithId();
        final Term source = Generator.generateTermWithId();
        final Term intermediate = Generator.generateTermWithId();
        final Term target = Generator.generateTermWithId();
        transactional(() -> {
            em.persist(subjectVocabulary, descriptorFactory.vocabularyDescriptor(subjectVocabulary));
            em.persist(intermediateVocabulary, descriptorFactory.vocabularyDescriptor(intermediateVocabulary));
            em.persist(targetVocabulary, descriptorFactory.vocabularyDescriptor(targetVocabulary));
            source.setGlossary(subjectVocabulary.getGlossary().getUri());
            em.persist(source, descriptorFactory.termDescriptor(subjectVocabulary));
            intermediate.setGlossary(intermediateVocabulary.getGlossary().getUri());
            em.persist(intermediate, descriptorFactory.termDescriptor(intermediateVocabulary));
            target.setGlossary(targetVocabulary.getGlossary().getUri());
            em.persist(target, descriptorFactory.termDescriptor(targetVocabulary));
            Generator.addTermInVocabularyRelationship(source, subjectVocabulary.getUri(), em);
            Generator.addTermInVocabularyRelationship(intermediate, intermediateVocabulary.getUri(), em);
            Generator.addTermInVocabularyRelationship(target, targetVocabulary.getUri(), em);
        });
        source.setRelatedMatch(Set.of(new TermInfo(intermediate)));
        intermediate.setExactMatchTerms(Set.of(new TermInfo(target)));
        transactional(() -> em.merge(source, descriptorFactory.termDescriptor(subjectVocabulary)));
        // Separate transactions to prevent OWLEntityExistsExceptions
        transactional(() -> em.merge(intermediate, descriptorFactory.termDescriptor(intermediateVocabulary)));

        final Set<URI> result = sut.getRelatedVocabularies(subjectVocabulary.getUri());
        assertEquals(Set.of(intermediateVocabulary.getUri(), targetVocabulary.getUri()), result);
    }

    @Test
    void getRelatedVocabulariesResolvesIndirectlyRelatedVocabulariesViaVocabularyImports() {
        final Vocabulary subjectVocabulary = Generator.generateVocabularyWithId();
        final Vocabulary intermediateVocabulary = Generator.generateVocabularyWithId();
        subjectVocabulary.setImportedVocabularies(Set.of(intermediateVocabulary.getUri()));
        final Vocabulary targetVocabulary = Generator.generateVocabularyWithId();
        intermediateVocabulary.setImportedVocabularies(Set.of(targetVocabulary.getUri()));
        transactional(() -> {
            em.persist(subjectVocabulary, descriptorFactory.vocabularyDescriptor(subjectVocabulary));
            em.persist(intermediateVocabulary, descriptorFactory.vocabularyDescriptor(intermediateVocabulary));
            em.persist(targetVocabulary, descriptorFactory.vocabularyDescriptor(targetVocabulary));
        });

        final Set<URI> result = sut.getRelatedVocabularies(subjectVocabulary.getUri());
        assertEquals(Set.of(intermediateVocabulary.getUri(), targetVocabulary.getUri()), result);
    }
}
