package cz.cvut.kbss.termit.persistence.dao.statistics;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.dto.statistics.TermFrequencyDto;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.Workspace;
import cz.cvut.kbss.termit.persistence.PersistenceUtils;
import cz.cvut.kbss.termit.util.ConfigParam;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Utils;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.io.File;
import java.util.List;

@Repository
public class StatisticsDao {

    private final EntityManager em;

    private final Configuration config;

    private final PersistenceUtils persistenceUtils;

    @Autowired
    public StatisticsDao(EntityManager em, Configuration config, PersistenceUtils persistenceUtils) {
        this.em = em;
        this.config = config;
        this.persistenceUtils = persistenceUtils;
    }

    /**
     * Gets term frequency per vocabulary statistics in JSON-LD.
     *
     * @param workspace Workspace whose vocabularies will be taken into account
     * @return List of term frequency information instances
     */
    public List<TermFrequencyDto> getTermFrequencyStatistics(Workspace workspace) {
        String query = Utils.loadQuery("statistics" + File.separator + "termFrequency.rq");
        return em.createNativeQuery(query, "TermFrequencyDto")
                 .setParameter("contexts", persistenceUtils.getWorkspaceVocabularyContexts(workspace))
                 .setParameter("lang", config.get(ConfigParam.LANGUAGE))
                 .getResultList();
    }

    /**
     * Gets the distribution of types among terms in the specified vocabulary.
     *
     * @param workspace  Workspace from which the statistics should be calculated
     * @param vocabulary Vocabulary in which the term type distribution should be calculated
     * @return List of term type frequency information instance. Each instance represent one type
     */
    public List<TermFrequencyDto> getTermTypeFrequencyStatistics(Workspace workspace, Vocabulary vocabulary, List<Term> leafTypes) {
        String query = Utils.loadQuery("statistics" + File.separator + "termTypeFrequency.rq");
        query = query + " VALUES ?t { " + leafTypes.stream().map(t -> "<"+t.getUri().toString()+">").collect(
            Collectors.joining(" "))+ " }";
        return em.createNativeQuery(query, "TermFrequencyDto")
                 .setParameter("g", persistenceUtils.resolveVocabularyContext(workspace, vocabulary.getUri()))
                 .setParameter("vocabulary", vocabulary)
                 .setParameter("lang", config.get(ConfigParam.LANGUAGE))
                 .getResultList();
    }
}
