package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.termit.dto.statistics.CountableAssetType;
import cz.cvut.kbss.termit.dto.statistics.DistributionDto;
import cz.cvut.kbss.termit.dto.statistics.TermTypeDistributionDto;
import cz.cvut.kbss.termit.service.language.LanguageService;
import jakarta.annotation.Nonnull;

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

    /**
     * Gets the number of items of the specified type.
     *
     * @param assetType Type of asset to count
     * @return Number of items
     */
    int getAssetCount(@Nonnull CountableAssetType assetType);

    /**
     * Gets statistics of distribution of types of terms in vocabularies.
     * <p>
     * Recognized types are taken from {@link LanguageService#getTermTypes()}
     *
     * @return List of distribution objects for each vocabulary
     */
    List<TermTypeDistributionDto> getTermTypeDistribution();
}
