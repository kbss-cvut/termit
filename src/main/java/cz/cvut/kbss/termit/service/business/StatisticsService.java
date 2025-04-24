/*
 * TermIt
 * Copyright (C) 2025 Czech Technical University in Prague
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
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
