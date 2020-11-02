package cz.cvut.kbss.termit.rest;

import com.fasterxml.jackson.core.type.TypeReference;
import cz.cvut.kbss.termit.dto.statistics.TermFrequencyDto;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.service.business.StatisticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StatisticsControllerTest extends BaseControllerTestRunner {

    @Mock
    private StatisticsService statisticsService;

    @InjectMocks
    private StatisticsController sut;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        super.setUp(sut);
    }

    @Test
    void getTermFrequencyStatisticsRetrievesStatisticsFromService() throws Exception {
        final List<TermFrequencyDto> data = Arrays.asList(new TermFrequencyDto(Generator.generateUri(), 1, "Test one"),
                new TermFrequencyDto(Generator.generateUri(), 2, "Test two"));
        when(statisticsService.getTermFrequencyStatistics()).thenReturn(data);
        final MvcResult mvcResult = mockMvc.perform(get("/statistics/term-frequency")).andExpect(status().isOk())
                                           .andReturn();
        final List<TermFrequencyDto> result = readValue(mvcResult, new TypeReference<List<TermFrequencyDto>>() {
        });
        assertEquals(data, result);
        verify(statisticsService).getTermFrequencyStatistics();
    }

    @Test
    void getTermTypeFrequencyStatisticsRetrievesStatisticsForSpecifiedVocabularyFromService() throws Exception {
        final List<TermFrequencyDto> data = Arrays.asList(new TermFrequencyDto(Generator.generateUri(), 1, "Type one"),
                new TermFrequencyDto(Generator.generateUri(), 2, "Type two"));
        final Vocabulary vocabulary = Generator.generateVocabularyWithId();
        when(statisticsService.getRequiredVocabulary(vocabulary.getUri())).thenReturn(vocabulary);
        when(statisticsService.getTermTypeFrequencyStatistics(any())).thenReturn(data);

        final MvcResult mvcResult = mockMvc
                .perform(get("/statistics/term-type-frequency").param("vocabulary", vocabulary.getUri().toString()))
                .andExpect(status().isOk()).andReturn();
        final List<TermFrequencyDto> result = readValue(mvcResult, new TypeReference<List<TermFrequencyDto>>() {
        });
        assertEquals(data, result);
        verify(statisticsService).getTermTypeFrequencyStatistics(vocabulary);
    }
}
