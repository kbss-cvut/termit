package cz.cvut.kbss.termit.persistence.dao.statistics;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.model.descriptors.EntityDescriptor;
import cz.cvut.kbss.termit.dto.statistics.TermFrequencyDto;
import cz.cvut.kbss.termit.dto.workspace.VocabularyInfo;
import cz.cvut.kbss.termit.dto.workspace.WorkspaceMetadata;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.environment.WorkspaceGenerator;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.Workspace;
import cz.cvut.kbss.termit.persistence.dao.BaseDaoTestRunner;
import cz.cvut.kbss.termit.persistence.dao.workspace.WorkspaceMetadataProvider;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;

class StatisticsDaoTest extends BaseDaoTestRunner {

    @Autowired
    private EntityManager em;

    @Autowired
    private StatisticsDao sut;

    @Autowired
    private WorkspaceMetadataProvider wsMetadataProvider;

    @Test
    void getTermFrequencyStatisticsLoadsFrequencyStatisticsForSpecifiedWorkspace() {
        enableRdfsInference(em);
        final Workspace ws = WorkspaceGenerator.generateWorkspace();
        final Vocabulary vocabulary = saveVocabulary(ws);
        final int matchingCount = generateTerms(vocabulary);

        final List<TermFrequencyDto> result = sut.getTermFrequencyStatistics(ws);
        assertEquals(1, result.size());
        assertEquals(matchingCount, result.get(0).getCount());
        assertEquals(vocabulary.getLabel(), result.get(0).getLabel());
        assertEquals(vocabulary.getUri(), result.get(0).getId());
    }

    private Vocabulary saveVocabulary(Workspace workspace) {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        wsMetadataProvider.loadWorkspace(workspace);
        final WorkspaceMetadata metadata = new WorkspaceMetadata(workspace);
        doReturn(metadata).when(wsMetadataProvider).getWorkspaceMetadata(workspace.getUri());
        metadata.setVocabularies(Collections.singletonMap(vocabulary.getUri(),
                new VocabularyInfo(vocabulary.getUri(), vocabulary.getUri(), vocabulary.getUri())));
        transactional(() -> {
            em.persist(workspace, new EntityDescriptor(workspace.getUri()));
            em.persist(vocabulary, new EntityDescriptor(vocabulary.getUri()));
            try (final RepositoryConnection conn = em.unwrap(Repository.class).getConnection()) {
                conn.begin();
                conn.add(Generator
                        .generateWorkspaceReferences(Collections.singleton(vocabulary), workspace));
                conn.commit();
            }
        });
        return vocabulary;
    }

    private int generateTerms(Vocabulary vocabulary) {
        int count = 0;
        for (int i = 0; i < 10; i++) {
            final Term t = Generator.generateTermWithId(vocabulary.getUri());
            if (Generator.randomBoolean()) {
                count++;
                transactional(() -> {
                    em.persist(t, new EntityDescriptor(vocabulary.getUri()));
                    Generator.addTermInVocabularyRelationship(t, vocabulary.getUri(), em);
                });
            } else {
                transactional(() -> {
                    em.persist(t);
                    Generator.addTermInVocabularyRelationship(t, vocabulary.getUri(), em);
                });
            }
        }
        return count;
    }
}
