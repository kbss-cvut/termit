package cz.cvut.kbss.termit.rest;

import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.environment.config.TestRestSecurityConfig;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.business.VocabularyService;
import cz.cvut.kbss.termit.util.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static cz.cvut.kbss.termit.environment.Generator.generateVocabulary;
import static cz.cvut.kbss.termit.util.Constants.REST_MAPPING_PATH;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VocabularyController.class)
@Import({TestRestSecurityConfig.class})
class VocabularyControllerSecurityTest extends BaseControllerTestRunner {

    private static final String PATH = REST_MAPPING_PATH + "/vocabularies";

    @MockBean
    private VocabularyService serviceMock;

    @MockBean
    private IdentifierResolver idResolverMock;

    @Autowired
    private Configuration configuration;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        configuration.getNamespace().setVocabulary(Environment.BASE_URI + "/");
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
}
