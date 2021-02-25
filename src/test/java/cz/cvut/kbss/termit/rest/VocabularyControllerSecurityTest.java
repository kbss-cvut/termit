package cz.cvut.kbss.termit.rest;

import static cz.cvut.kbss.termit.environment.Generator.generateVocabulary;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.environment.config.TestConfig;
import cz.cvut.kbss.termit.environment.config.TestRestSecurityConfig;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.rest.handler.RestExceptionHandler;
import cz.cvut.kbss.termit.security.JwtUtils;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.VocabularyService;
import cz.cvut.kbss.termit.service.security.SecurityUtils;
import cz.cvut.kbss.termit.util.ConfigParam;
import cz.cvut.kbss.termit.util.Configuration;
import java.net.URI;
import java.util.List;
import javax.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {TestConfig.class,
    TestRestSecurityConfig.class,
    VocabularyControllerSecurityTest.Config.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@WebAppConfiguration
class VocabularyControllerSecurityTest extends BaseControllerTestRunner {

    private static final String PATH = "/vocabularies";
    private static final String NAMESPACE =
        "http://onto.fel.cvut.cz/ontologies/termit/vocabularies/";
    private static final String FRAGMENT = "test";
    private static final URI VOCABULARY_URI = URI.create(NAMESPACE + FRAGMENT);

    @Autowired
    private Filter springSecurityFilterChain;

    @Autowired
    private WebApplicationContext context;

    @Mock
    private VocabularyService serviceMock;

    @Mock
    private IdentifierResolver idResolverMock;

    @Mock
    private Configuration configMock;

    @InjectMocks
    private VocabularyController sut;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        super.setUp(sut);
        when(configMock.get(ConfigParam.NAMESPACE_VOCABULARY))
            .thenReturn(Environment.BASE_URI + "/");
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity(springSecurityFilterChain))
            .build();
    }

    @Test
    void createVocabularyThrowsAuthorizationExceptionForRestrictedUsers() throws Exception {
        // This one is restricted
        Environment.setCurrentUser(Generator.generateUserAccountWithPassword());

        final Vocabulary vocabulary = generateVocabulary();
        vocabulary.setUri(Generator.generateUri());

        mockMvc.perform(
            post(PATH).content(toJson(vocabulary)).contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isForbidden());
        verify(serviceMock, never()).update(any());
    }

    @Test
    void removeVocabularyThrowsAuthorizationExceptionForRestrictedUsers() throws Exception {
        // This one is restricted
        Environment.setCurrentUser(Generator.generateUserAccountWithPassword());

        final Vocabulary vocabulary = generateVocabulary();
        vocabulary.setUri(Generator.generateUri());
        final String fragment = IdentifierResolver.extractIdentifierFragment(vocabulary.getUri());
        mockMvc.perform(
            delete(PATH + "/" + fragment))
            .andExpect(status().isForbidden());
        verify(serviceMock, never()).update(any());
    }

    @Test
    void updateVocabularyThrowsAuthorizationExceptionForRestrictedUsers() throws Exception {
        // This one is restricted
        Environment.setCurrentUser(Generator.generateUserAccountWithPassword());

        final Vocabulary vocabulary = generateVocabulary();
        vocabulary.setUri(VOCABULARY_URI);
        when(idResolverMock.resolveIdentifier(eq(ConfigParam.NAMESPACE_VOCABULARY), any()))
            .thenReturn(VOCABULARY_URI);
        when(serviceMock.exists(VOCABULARY_URI)).thenReturn(true);
        mockMvc.perform(put(PATH + "/test").contentType(MediaType.APPLICATION_JSON_VALUE)
            .content(toJson(vocabulary)))
            .andExpect(status().isForbidden());
        verify(serviceMock, never()).update(any());
    }

    /**
     * Inner class is necessary to provide the controller as a bean, so that the WebApplicationContext can map it.
     */
    @EnableWebMvc
    @org.springframework.context.annotation.Configuration
    public static class Config implements WebMvcConfigurer {
        @Mock
        private VocabularyService userService;

        @Mock
        private SecurityUtils securityUtilsMock;

        @InjectMocks
        private VocabularyController controller;

        Config() {
            MockitoAnnotations.initMocks(this);
        }

        @Bean
        public VocabularyService userService() {
            return userService;
        }

        @Bean
        public VocabularyController userController() {
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
