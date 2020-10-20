package cz.cvut.kbss.termit.persistence.dao.statistics;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.dto.statistics.TermFrequencyDto;
import cz.cvut.kbss.termit.model.Workspace;
import cz.cvut.kbss.termit.persistence.PersistenceUtils;
import cz.cvut.kbss.termit.util.ConfigParam;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Utils;
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
}
