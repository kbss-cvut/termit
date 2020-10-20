package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.termit.dto.statistics.TermFrequencyDto;
import cz.cvut.kbss.termit.persistence.dao.statistics.StatisticsDao;
import cz.cvut.kbss.termit.persistence.dao.workspace.WorkspaceMetadataProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StatisticsService {

    private final WorkspaceMetadataProvider wsMetadataProvider;

    private final StatisticsDao statisticsDao;

    @Autowired
    public StatisticsService(WorkspaceMetadataProvider wsMetadataProvider, StatisticsDao statisticsDao) {
        this.wsMetadataProvider = wsMetadataProvider;
        this.statisticsDao = statisticsDao;
    }

    /**
     * Gets statistics of term frequency across vocabularies in the current workspace.
     *
     * @return List of term frequency DTOs
     */
    public List<TermFrequencyDto> getTermFrequencyStatistics() {
        return statisticsDao.getTermFrequencyStatistics(wsMetadataProvider.getCurrentWorkspace());
    }
}
