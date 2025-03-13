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
