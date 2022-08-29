package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.termit.config.Profiles;
import cz.cvut.kbss.termit.dto.ConfigurationDto;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.environment.config.TestRestSecurityConfig;
import cz.cvut.kbss.termit.model.UserRole;
import cz.cvut.kbss.termit.service.config.ConfigurationProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static cz.cvut.kbss.termit.util.Constants.REST_MAPPING_PATH;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WithMockUser
@WebMvcTest(ConfigurationController.class)
@Import({TestRestSecurityConfig.class})
@ActiveProfiles(Profiles.JWT_AUTH)
public class ConfigurationControllerSecurityTest extends BaseControllerTestRunner {

    private static final String PATH = REST_MAPPING_PATH + "/configuration";

    @MockBean
    private ConfigurationProvider configurationProvider;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Environment.resetCurrentUser();
    }

    @Test
    void getConfigurationReturnsFullConfigurationWhenUserIsAuthenticated() throws Exception {
        Environment.setCurrentUser(Generator.generateUserAccountWithPassword());
        final ConfigurationDto config = generateConfiguration();
        when(configurationProvider.getConfiguration()).thenReturn(config);
        final MvcResult mvcResult = mockMvc.perform(get(PATH)).andExpect(status().isOk()).andReturn();
        final ConfigurationDto result = readValue(mvcResult, ConfigurationDto.class);
        assertNotNull(result);
        assertEquals(config.getLanguage(), result.getLanguage());
        assertEquals(config.getRoles(), result.getRoles());
    }

    private static ConfigurationDto generateConfiguration() {
        final ConfigurationDto config = new ConfigurationDto();
        config.setLanguage(Environment.LANGUAGE);
        config.setRoles(IntStream.range(0, 3).mapToObj(i -> {
            final UserRole r = new UserRole();
            r.setUri(Generator.generateUri());
            r.setLabel(MultilingualString.create("Role - " + i, Environment.LANGUAGE));
            return r;
        }).collect(Collectors.toSet()));
        return config;
    }

    @WithAnonymousUser
    @Test
    void getConfigurationReturnsConfigurationWithoutRolesWhenUserIsNotAuthenticated() throws Exception {
        final ConfigurationDto config = generateConfiguration();
        when(configurationProvider.getConfiguration()).thenReturn(config);
        final MvcResult mvcResult = mockMvc.perform(get(PATH)).andExpect(status().isOk()).andReturn();
        final ConfigurationDto result = readValue(mvcResult, ConfigurationDto.class);
        assertNotNull(result);
        assertEquals(config.getLanguage(), result.getLanguage());
        assertThat(config.getRoles(), anyOf(nullValue(), empty()));
    }
}
