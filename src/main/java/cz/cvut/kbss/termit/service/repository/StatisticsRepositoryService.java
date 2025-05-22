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
package cz.cvut.kbss.termit.service.repository;

import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.RdfsResource;
import cz.cvut.kbss.termit.dto.statistics.CountableAssetType;
import cz.cvut.kbss.termit.dto.statistics.DistributionDto;
import cz.cvut.kbss.termit.dto.statistics.TermTypeDistributionDto;
import cz.cvut.kbss.termit.persistence.dao.StatisticsDao;
import cz.cvut.kbss.termit.service.business.StatisticsService;
import cz.cvut.kbss.termit.service.language.LanguageService;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StatisticsRepositoryService implements StatisticsService {

    private final StatisticsDao dao;

    private final LanguageService languageService;

    public StatisticsRepositoryService(StatisticsDao dao, LanguageService languageService) {
        this.dao = dao;
        this.languageService = languageService;
    }

    @Transactional(readOnly = true)
    @Override
    public List<DistributionDto> getTermDistribution() {
        return dao.getTermDistribution();
    }

    @Transactional(readOnly = true)
    @Override
    public int getAssetCount(@Nonnull CountableAssetType assetType) {
        return dao.getAssetCount(assetType);
    }

    @Transactional(readOnly = true)
    @Override
    public List<TermTypeDistributionDto> getTermTypeDistribution() {
        final List<RdfsResource> types = languageService.getTermTypes().stream()
                                                        .map(t -> new RdfsResource(t.getUri(), t.getLabel(), null,
                                                                                   SKOS.CONCEPT))
                                                        .toList();
        return dao.getTermTypeDistribution(types);
    }
}
