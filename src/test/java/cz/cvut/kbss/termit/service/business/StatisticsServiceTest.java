package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.termit.dto.statistics.TermFrequencyDto;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.environment.WorkspaceGenerator;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.model.Workspace;
import cz.cvut.kbss.termit.persistence.dao.statistics.StatisticsDao;
import cz.cvut.kbss.termit.persistence.dao.workspace.WorkspaceMetadataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatisticsServiceTest {

    @Mock
    private WorkspaceMetadataProvider wsMetadataProvider;

    @Mock
    private StatisticsDao dao;

    @InjectMocks
    private StatisticsService sut;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    void getTermFrequencyStatisticsUsesCurrentWorkspaceToGetStatisticsFromDao() {
        final Workspace ws = WorkspaceGenerator.generateWorkspace();
        when(wsMetadataProvider.getCurrentWorkspace()).thenReturn(ws);
        final List<TermFrequencyDto> expected = Collections
                .singletonList(new TermFrequencyDto(Generator.generateUri(), 1, "test"));
        when(dao.getTermFrequencyStatistics(any())).thenReturn(expected);

        final List<TermFrequencyDto> result = sut.getTermFrequencyStatistics();
        assertEquals(expected, result);
        verify(dao).getTermFrequencyStatistics(ws);
        verify(wsMetadataProvider).getCurrentWorkspace();
    }

    @Test
    void getTermTypeFrequencyStatisticsUsesCurrentWorkspaceToGetStatisticsFromDao() {
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        final Workspace ws = WorkspaceGenerator.generateWorkspace();
        when(wsMetadataProvider.getCurrentWorkspace()).thenReturn(ws);
        final List<TermFrequencyDto> expected = Collections
                .singletonList(new TermFrequencyDto(Generator.generateUri(), 1, "test"));
        when(dao.getTermTypeFrequencyStatistics(any(), any())).thenReturn(expected);

        final List<TermFrequencyDto> result = sut.getTermTypeFrequencyStatistics(vocabulary);
        assertEquals(expected, result);
        verify(dao).getTermTypeFrequencyStatistics(ws, vocabulary);
        verify(wsMetadataProvider).getCurrentWorkspace();
    }
}
