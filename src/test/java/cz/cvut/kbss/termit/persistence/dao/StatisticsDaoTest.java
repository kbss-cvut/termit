package cz.cvut.kbss.termit.persistence.dao;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.dto.statistics.DistributionDto;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.context.DescriptorFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatisticsDaoTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private DescriptorFactory descriptorFactory;

    @Autowired
    private StatisticsDao sut;

    @Test
    void getTermDistributionReturnsTermDistributionWithConsolidatedTranslationsOfVocabularyLabels() {
        final Vocabulary v = Generator.generateVocabularyWithId();
        v.getLabel().set("cs", "Název v češtině");
        final Term term = Generator.generateTermWithId(v.getUri());
        transactional(() -> {
            em.persist(v, descriptorFactory.vocabularyDescriptor(v));
            em.persist(term, descriptorFactory.termDescriptor(term));
            Generator.addTermInVocabularyRelationship(term, v.getUri(), em);
        });

        final List<DistributionDto> result = sut.getTermDistribution();
        assertEquals(1, result.size());
        assertEquals(v.getUri(), result.get(0).getResource().getUri());
        assertEquals(Set.of(Environment.LANGUAGE, "cs"), result.get(0).getResource().getLabel().getLanguages());
        assertEquals(1, result.get(0).getCount());
    }
}
