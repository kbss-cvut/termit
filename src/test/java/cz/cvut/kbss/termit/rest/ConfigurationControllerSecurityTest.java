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
package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.termit.dto.ConfigurationDto;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.environment.config.TestRestSecurityConfig;
import cz.cvut.kbss.termit.model.UserRole;
import cz.cvut.kbss.termit.service.config.ConfigurationProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static cz.cvut.kbss.termit.util.Constants.REST_MAPPING_PATH;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Execution(ExecutionMode.SAME_THREAD)
@WithMockUser
@WebMvcTest(ConfigurationController.class)
@Import({TestRestSecurityConfig.class})
public class ConfigurationControllerSecurityTest extends BaseControllerTestRunner {

    private static final String PATH = REST_MAPPING_PATH + "/configuration";

    @MockitoBean
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
            final UserRole r = new UserRole(Generator.generateUri());
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
