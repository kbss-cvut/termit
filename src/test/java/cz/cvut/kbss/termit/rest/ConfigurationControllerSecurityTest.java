package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.termit.dto.ConfigurationDto;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.environment.config.TestConfig;
import cz.cvut.kbss.termit.environment.config.TestRestSecurityConfig;
import cz.cvut.kbss.termit.model.UserRole;
import cz.cvut.kbss.termit.rest.handler.RestExceptionHandler;
import cz.cvut.kbss.termit.security.JwtUtils;
import cz.cvut.kbss.termit.service.config.ConfigurationProvider;
import cz.cvut.kbss.termit.service.security.SecurityUtils;
import cz.cvut.kbss.termit.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.servlet.Filter;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {TestConfig.class,
        TestRestSecurityConfig.class,
        ConfigurationControllerSecurityTest.Config.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@WebAppConfiguration
public class ConfigurationControllerSecurityTest extends BaseControllerTestRunner {

    private static final String PATH = "/configuration";

    @Autowired
    private Filter springSecurityFilterChain;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ConfigurationProvider configurationProvider;

    @InjectMocks
    private ConfigurationController sut;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        super.setUp(sut);
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity(springSecurityFilterChain)).build();
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
        config.setLanguage(Constants.DEFAULT_LANGUAGE);
        config.setRoles(IntStream.range(0, 3).mapToObj(i -> {
            final UserRole r = new UserRole();
            r.setUri(Generator.generateUri());
            r.setLabel(MultilingualString.create("Role - " + i, Constants.DEFAULT_LANGUAGE));
            return r;
        }).collect(Collectors.toSet()));
        return config;
    }

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

    /**
     * Inner class is necessary to provide the controller as a bean, so that the WebApplicationContext can map it.
     */
    @EnableWebMvc
    @org.springframework.context.annotation.Configuration
    public static class Config implements WebMvcConfigurer {
        @Mock
        private ConfigurationProvider configurationProvider;

        @Mock
        private SecurityUtils securityUtilsMock;

        @InjectMocks
        private ConfigurationController controller;

        Config() {
            MockitoAnnotations.initMocks(this);
        }

        @Bean
        public ConfigurationProvider configurationProvider() {
            return configurationProvider;
        }

        @Bean
        public ConfigurationController userController() {
            return controller;
        }

        @Bean
        public SecurityUtils securityUtils() {
            return securityUtilsMock;
        }

        @Bean
        public RestExceptionHandler restExceptionHandler() {
            return new RestExceptionHandler();
        }

        @Bean
        public JwtUtils jwtUtils(cz.cvut.kbss.termit.util.Configuration config) {
            return new JwtUtils(config);
        }

        @Override
        public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
            converters.add(Environment.createJsonLdMessageConverter());
            converters.add(Environment.createDefaultMessageConverter());
            converters.add(Environment.createStringEncodingMessageConverter());
        }
    }
}
