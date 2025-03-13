package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.termit.dto.statistics.DistributionDto;

import java.util.List;

/**
 * Provides statistics on data.
 */
public interface StatisticsService {

    /**
     * Gets statistics of term distribution in vocabularies.
     *
     * @return List of term count per vocabulary
     */
    List<DistributionDto> getTermDistribution();
}
