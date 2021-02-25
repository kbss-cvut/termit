package cz.cvut.kbss.termit.rest;

import static cz.cvut.kbss.termit.util.Constants.DEFAULT_TERM_NAMESPACE_SEPARATOR;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
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
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.rest.handler.RestExceptionHandler;
import cz.cvut.kbss.termit.security.JwtUtils;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.TermService;
import cz.cvut.kbss.termit.service.security.SecurityUtils;
import cz.cvut.kbss.termit.util.ConfigParam;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
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
    TermControllerSecurityTest.Config.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@WebAppConfiguration
class TermControllerSecurityTest extends BaseControllerTestRunner {

    private static final String PATH = "/vocabularies/";
    private static final String VOCABULARY_NAME = "metropolitan-plan";
    private static final String TERM_NAME = "locality";
    private static final String VOCABULARY_URI = Environment.BASE_URI + "/" + VOCABULARY_NAME;
    private static final String NAMESPACE =
        VOCABULARY_URI + Constants.DEFAULT_TERM_NAMESPACE_SEPARATOR + "/";

    @Autowired
    private Filter springSecurityFilterChain;

    @Autowired
    private WebApplicationContext context;


    @Mock
    private IdentifierResolver idResolverMock;

    @Mock
    private TermService termServiceMock;

    @Mock
    private Configuration configMock;

    @InjectMocks
    private TermController sut;

    private cz.cvut.kbss.termit.model.Vocabulary vocabulary;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        super.setUp(sut);
        this.vocabulary = Generator.generateVocabulary();
        when(configMock.get(ConfigParam.NAMESPACE_VOCABULARY))
            .thenReturn(Environment.BASE_URI + "/");
        when(configMock.get(ConfigParam.TERM_NAMESPACE_SEPARATOR))
            .thenReturn(DEFAULT_TERM_NAMESPACE_SEPARATOR);
        vocabulary.setLabel(VOCABULARY_NAME);
        vocabulary.setUri(URI.create(VOCABULARY_URI));
        // WebApplicationContext is required for proper security. Otherwise, standaloneSetup
        // could be used
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity(springSecurityFilterChain))
            .build();
    }

    private URI initTermUriResolution() {
        final URI termUri = URI.create(Environment.BASE_URI + "/" + VOCABULARY_NAME +
            Constants.DEFAULT_TERM_NAMESPACE_SEPARATOR + "/" + TERM_NAME);
        when(idResolverMock.resolveIdentifier(ConfigParam.NAMESPACE_VOCABULARY, VOCABULARY_NAME))
            .thenReturn(URI.create(VOCABULARY_URI));
        when(idResolverMock.buildNamespace(eq(VOCABULARY_URI), any())).thenReturn(NAMESPACE);
        when(idResolverMock.resolveIdentifier(NAMESPACE, TERM_NAME)).thenReturn(termUri);
        return termUri;
    }

    @Test
    void createRootTermThrowsAuthorizationExceptionForRestrictedUsers() throws Exception {
        // This one is restricted
        Environment.setCurrentUser(Generator.generateUserAccountWithPassword());

        final URI termUri = initTermUriResolution();
        final Term term = Generator.generateTerm();
        term.setUri(termUri);

        mockMvc.perform(post(PATH + VOCABULARY_NAME + "/terms").content(toJson(term)).contentType(
            MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isForbidden());
        verify(termServiceMock, never()).update(any());
    }

    @Test
    void updateThrowsAuthorizationExceptionForRestrictedUsers() throws Exception {
        // This one is restricted
        Environment.setCurrentUser(Generator.generateUserAccountWithPassword());

        final URI termUri = initTermUriResolution();
        final Term term = Generator.generateTerm();
        term.setUri(termUri);

        mockMvc.perform(
            put(PATH + VOCABULARY_NAME + "/terms/" + TERM_NAME).content(toJson(term)).contentType(
                MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isForbidden());
        verify(termServiceMock, never()).update(any());
    }

    @Test
    void deleteThrowsAuthorizationExceptionForRestrictedUsers() throws Exception {
        // This one is restricted
        Environment.setCurrentUser(Generator.generateUserAccountWithPassword());
        mockMvc.perform(delete(PATH + VOCABULARY_NAME + "/terms/" + TERM_NAME).contentType(
            MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isForbidden());
        verify(termServiceMock, never()).update(any());
    }

    /**
     * Inner class is necessary to provide the controller as a bean, so that the
     * WebApplicationContext can map it.
     */
    @EnableWebMvc
    @org.springframework.context.annotation.Configuration
    public static class Config implements WebMvcConfigurer {
        @Mock
        private TermService userService;

        @Mock
        private SecurityUtils securityUtilsMock;

        @InjectMocks
        private TermController controller;

        Config() {
            MockitoAnnotations.initMocks(this);
        }

        @Bean
        public TermService userService() {
            return userService;
        }

        @Bean
        public TermController userController() {
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
