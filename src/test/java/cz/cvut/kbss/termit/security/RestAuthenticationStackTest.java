package cz.cvut.kbss.termit.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.environment.config.TestAuthenticationStackConfig;
import cz.cvut.kbss.termit.environment.config.TestConfig;
import cz.cvut.kbss.termit.environment.config.TestServiceConfig;
import cz.cvut.kbss.termit.model.PersonalAccessToken;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.persistence.dao.PersonalAccessTokenDao;
import cz.cvut.kbss.termit.persistence.dao.UserAccountDao;
import cz.cvut.kbss.termit.rest.PersonalAccessTokenController;
import cz.cvut.kbss.termit.rest.UserController;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.Utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for the authentication stack using MockMvc.
 * This test initializes almost whole application and mocks only DAOs,
 * the goal is to test the security configuration close to production.
 */
@ContextConfiguration(classes = {
        TestAuthenticationStackConfig.class,
        TestConfig.class,
        TestServiceConfig.class},
                      initializers = {ConfigDataApplicationContextInitializer.class})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Execution(ExecutionMode.SAME_THREAD)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class RestAuthenticationStackTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtUtils validJwtUtils;

    @MockitoBean
    private UserAccountDao userAccountDao;

    @MockitoBean
    private PersonalAccessTokenDao patDao;

    private UserAccount staticUser;
    private String plainPassword;
    private PersonalAccessToken patToken;

    @BeforeEach
    void setUp() {
        plainPassword = "secretPassword";

        staticUser = new UserAccount();
        staticUser.setFirstName("testFirstName");
        staticUser.setLastName("testLastName");
        staticUser.setUsername("testUsername");
        staticUser.setPassword(passwordEncoder.encode(plainPassword));
        staticUser.setUri(URI.create("http://onto.fel.cvut.cz/ontologies/application/termit/test-static-user"));

        patToken = new PersonalAccessToken();
        patToken.setUri(Generator.generateUri());
        patToken.setOwner(staticUser);

        when(userAccountDao.findByUsername(eq(staticUser.getUsername()))).thenReturn(Optional.of(staticUser));
        when(userAccountDao.find(eq(staticUser.getUri()))).thenReturn(Optional.of(staticUser));
        when(userAccountDao.update(any())).then(invocation -> staticUser);
    }

    private JwtUtils getInvalidKeyJwtUtils() {
        Configuration config = new Configuration();
        config.getJwt().setSecretKey("TermItSecretKeyThatWillNotMatchSecretForTesting");
        return new JwtUtils(objectMapper, config);
    }

    private String generateJwtWithInvalidSignature() {
        JwtUtils invalidKeyJwtUtils = getInvalidKeyJwtUtils();
        return SecurityConstants.JWT_TOKEN_PREFIX + invalidKeyJwtUtils.generateToken(staticUser, List.of());
    }

    private String generateValidJWT() {
        return SecurityConstants.JWT_TOKEN_PREFIX + validJwtUtils.generateToken(staticUser, List.of());
    }

    private String generateValidPAT() {
        return SecurityConstants.JWT_TOKEN_PREFIX + validJwtUtils.generatePAT(patToken);
    }

    private String generatePATWithInvalidSignature() {
        JwtUtils invalidKeyJwtUtils = getInvalidKeyJwtUtils();
        return SecurityConstants.JWT_TOKEN_PREFIX + invalidKeyJwtUtils.generatePAT(patToken);
    }

    private ResultActions makeRequestWithJwt(String authToken) throws Exception {
        return mockMvc.perform(get(Constants.REST_MAPPING_PATH + UserController.PATH + UserController.CURRENT_USER_PATH)
                .header(HttpHeaders.AUTHORIZATION, authToken));
    }

    private String performLogin() throws Exception {
        MvcResult result = mockMvc.perform(post(SecurityConstants.LOGIN_PATH)
                                          .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                                          .param("username", staticUser.getUsername())
                                          .param("password", plainPassword))
                                  .andExpect(status().isOk())
                                  .andReturn();

        String authToken = result.getResponse().getHeader(HttpHeaders.AUTHORIZATION);
        assertFalse(Utils.isBlank(authToken));
        return authToken;
    }

    private String requestPAT(String userJWT) throws Exception {
        MvcResult result = mockMvc.perform(post(Constants.REST_MAPPING_PATH + PersonalAccessTokenController.PATH)
                                          .header(HttpHeaders.AUTHORIZATION, userJWT))
                .andExpect(status().isOk())
                .andReturn();
        String patToken = result.getResponse().getContentAsString();
        assertFalse(Utils.isBlank(patToken));
        return SecurityConstants.JWT_TOKEN_PREFIX + patToken;
    }

    @Test
    void validLoginReturnsValidUserJwtInAuthorizationHeader() throws Exception {
        String authToken = performLogin();
        // Verify that the token works
        makeRequestWithJwt(authToken)
                .andExpect(status().isOk());
    }

    @Test
    void validSignatureTokenIsAccepted() throws Exception {
        String validJwtToken = generateValidJWT();
        makeRequestWithJwt(validJwtToken)
                .andExpect(status().isOk());
    }

    @Test
    void invalidSignatureTokenIsRejected() throws Exception {
        makeRequestWithJwt(generateJwtWithInvalidSignature())
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createPATCreatesValidPAT() throws Exception {
        AtomicReference<PersonalAccessToken> createdToken = new AtomicReference<>();
        // store created token on persist
        doAnswer(i -> {
            createdToken.set(i.getArgument(0, PersonalAccessToken.class));
            return null;
        }).when(patDao).persist(any(PersonalAccessToken.class));
        // on find, return persisted token if ID matches
        doAnswer(i -> {
            PersonalAccessToken ref = createdToken.get();
            URI requestURI = i.getArgument(0, URI.class);
            if (requestURI.equals(ref.getUri())) {
                return Optional.of(ref);
            }
            return Optional.empty();
        }).when(patDao).find(any());

        final String userJWT = performLogin();
        final String PAT = requestPAT(userJWT);

        MvcResult result = makeRequestWithJwt(PAT)
                .andExpect(status().isOk())
                .andReturn();

        final UserAccount currentUser = objectMapper.readValue(result.getResponse().getContentAsString(), UserAccount.class);

        assertEquals(staticUser, currentUser);
    }

    @Test
    void PATWithInvalidSignatureIsRejected() throws Exception {
        makeRequestWithJwt(generatePATWithInvalidSignature())
                .andExpect(status().isUnauthorized());
    }

    @Test
    void PATWithValidSignatureIsAccepted() throws Exception {
        String validPAT = generateValidPAT();
        when(patDao.find(patToken.getUri())).thenReturn(Optional.of(patToken));
        makeRequestWithJwt(validPAT)
                .andExpect(status().isOk());
    }
}
