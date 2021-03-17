package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.termit.dto.ConfigurationDto;
import cz.cvut.kbss.termit.service.config.ConfigurationProvider;
import cz.cvut.kbss.termit.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ConfigurationControllerTest extends BaseControllerTestRunner {

    @Mock
    private ConfigurationProvider configurationProvider;

    @InjectMocks
    private ConfigurationController sut;

    @BeforeEach
    void setUp() {
        super.setUp(sut);
    }

    @Test
    void getConfigurationRetrievesConfigurationFromConfigProvider() throws Exception {
        final ConfigurationDto config = new ConfigurationDto();
        config.setLanguage(Constants.DEFAULT_LANGUAGE);
        when(configurationProvider.getConfiguration()).thenReturn(config);
        final MvcResult mvcResult = mockMvc.perform(get("/configuration")).andExpect(status().isOk()).andReturn();
        final ConfigurationDto result = readValue(mvcResult, ConfigurationDto.class);
        assertNotNull(result);
        assertEquals(config.getLanguage(), result.getLanguage());
    }
}
