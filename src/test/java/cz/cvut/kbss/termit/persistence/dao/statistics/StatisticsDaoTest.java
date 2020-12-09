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

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;

class StatisticsDaoTest extends BaseDaoTestRunner {

    private static final List<String> KNOWN_TYPES = Arrays.asList(
            "https://slovník.gov.cz/základní/pojem/typ-objektu",
            "https://slovník.gov.cz/základní/pojem/typ-vlastnosti",
            "https://slovník.gov.cz/základní/pojem/typ-vztahu",
            "https://slovník.gov.cz/základní/pojem/typ-události"
    );

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
        transactional(() -> em.persist(ws, new EntityDescriptor(ws.getUri())));
        final Vocabulary vocabulary = saveVocabulary(ws);
        final int matchingCount = generateTerms(vocabulary).size();

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

    private List<Term> generateTerms(Vocabulary vocabulary) {
        final List<Term> matching = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            final Term t = Generator.generateTermWithId(vocabulary.getUri());
            t.addType(KNOWN_TYPES.get(Generator.randomIndex(KNOWN_TYPES)));
            if (Generator.randomBoolean()) {
                transactional(() -> {
                    em.persist(t, new EntityDescriptor(vocabulary.getUri()));
                    Generator.addTermInVocabularyRelationship(t, vocabulary.getUri(), em);
                });
                matching.add(t);
            } else {
                transactional(() -> {
                    em.persist(t);
                    Generator.addTermInVocabularyRelationship(t, vocabulary.getUri(), em);
                });
            }
        }
        return matching;
    }

    @Test
    void getTermTypeFrequencyStatisticsLoadsTermTypeFrequencyStatisticsForSpecifiedWorkspaceAndVocabulary() {
        enableRdfsInference(em);
        final Workspace ws = WorkspaceGenerator.generateWorkspace();
        transactional(() -> em.persist(ws, new EntityDescriptor(ws.getUri())));
        final Vocabulary vocabulary = saveVocabulary(ws);
        final List<Term> terms = generateTerms(vocabulary);
        final List<Term> types = KNOWN_TYPES.stream().map(t -> {
            final Term type = new Term();
            type.setUri(URI.create(t));
            return type;
        }).collect(Collectors.toList());
        final List<TermFrequencyDto> expected = KNOWN_TYPES.stream().map(t -> {
            final TermFrequencyDto typeFreq = new TermFrequencyDto(URI.create(t), 0, t);
            typeFreq.setCount(Math.toIntExact(terms.stream().filter(term -> term.hasType(t)).count()));
            return typeFreq;
        }).filter(tfd -> tfd.getCount() > 0).sorted(Comparator.comparing(TermFrequencyDto::getCount).reversed()
                                                              .thenComparing(TermFrequencyDto::getLabel))
                                                           .collect(Collectors.toList());

        final List<TermFrequencyDto> result = sut.getTermTypeFrequencyStatistics(ws, vocabulary, types);
        assertEquals(expected, result);
    }
}
