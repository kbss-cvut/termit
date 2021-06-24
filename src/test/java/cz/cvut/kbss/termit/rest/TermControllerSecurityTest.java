package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.environment.config.TestRestSecurityConfig;
import cz.cvut.kbss.termit.model.Term;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.TermService;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;

import static cz.cvut.kbss.termit.util.Constants.REST_MAPPING_PATH;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TermController.class)
@Import({TestRestSecurityConfig.class})
class TermControllerSecurityTest extends BaseControllerTestRunner {

    private static final String PATH = REST_MAPPING_PATH + "/vocabularies/";
    private static final String VOCABULARY_NAME = "metropolitan-plan";
    private static final String TERM_NAME = "locality";
    private static final String VOCABULARY_URI = Environment.BASE_URI + "/" + VOCABULARY_NAME;
    private static final String NAMESPACE =
            VOCABULARY_URI + "/pojem/";


    @MockBean
    private IdentifierResolver idResolverMock;

    @MockBean
    private TermService termServiceMock;

    @Autowired
    private Configuration configuration;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        configuration.getNamespace().setVocabulary(Environment.BASE_URI + "/");
    }

    private URI initTermUriResolution() {
        final URI termUri = URI.create(Environment.BASE_URI + "/" + VOCABULARY_NAME +
                configuration.getNamespace().getTerm().getSeparator() + "/" + TERM_NAME);
        when(idResolverMock.resolveIdentifier(configuration.getNamespace().getVocabulary(), VOCABULARY_NAME))
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
}
