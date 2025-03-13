package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.termit.dto.statistics.DistributionDto;
import cz.cvut.kbss.termit.persistence.dao.StatisticsDao;
import cz.cvut.kbss.termit.service.business.StatisticsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StatisticsRepositoryService implements StatisticsService {

    private final StatisticsDao dao;

    public StatisticsRepositoryService(StatisticsDao dao) {
        this.dao = dao;
    }

    @Transactional(readOnly = true)
    @Override
    public List<DistributionDto> getTermDistribution() {
        return dao.getTermDistribution();
    }
}
