package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.termit.dto.statistics.TermFrequencyDto;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.persistence.dao.statistics.StatisticsDao;
import cz.cvut.kbss.termit.persistence.dao.workspace.WorkspaceMetadataProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;
import java.util.Objects;

@Service
public class StatisticsService {

    private final WorkspaceMetadataProvider wsMetadataProvider;

    private final VocabularyService vocabularyService;

    private final StatisticsDao statisticsDao;

    @Autowired
    public StatisticsService(WorkspaceMetadataProvider wsMetadataProvider,
                             VocabularyService vocabularyService,
                             StatisticsDao statisticsDao) {
        this.wsMetadataProvider = wsMetadataProvider;
        this.vocabularyService = vocabularyService;
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

    /**
     * Gets statistics of term types distribution within the specified vocabulary (in the current workspace).
     *
     * @param vocabulary Vocabulary whose term types to retrieve
     * @return List of term frequency DTOs, where the term is a specific term type
     */
    public List<TermFrequencyDto> getTermTypeFrequencyStatistics(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        return statisticsDao.getTermTypeFrequencyStatistics(wsMetadataProvider.getCurrentWorkspace(), vocabulary);
    }

    /**
     * Gets a reference to a vocabulary with the specified identifier.
     *
     * @param vocabularyId Vocabulary identifier
     * @return Vocabulary instance reference
     */
    public Vocabulary getRequiredVocabulary(URI vocabularyId) {
        return vocabularyService.getRequiredReference(vocabularyId);
    }
}
