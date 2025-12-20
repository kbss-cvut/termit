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
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.jackson.io.JacksonSerializer;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JoseHeaderNames;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import javax.crypto.SecretKey;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static cz.cvut.kbss.termit.security.JwtUtils.SIGNATURE_ALGORITHM;
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

    @Autowired
    private Configuration configuration;

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

        staticUser = Generator.generateUserAccount();
        staticUser.setUsername("testUsername");
        staticUser.setPassword(passwordEncoder.encode(plainPassword));
        staticUser.setUri(URI.create("http://onto.fel.cvut.cz/ontologies/application/termit/test-static-user"));

        patToken = new PersonalAccessToken();
        patToken.setUri(Generator.generateUri());
        patToken.setOwner(staticUser);

        setUserToContext(staticUser);
    }

    private void setUserToContext(UserAccount account) {
        when(userAccountDao.findByUsername(eq(account.getUsername()))).thenReturn(Optional.of(account));
        when(userAccountDao.find(eq(account.getUri()))).thenReturn(Optional.of(account));
        when(userAccountDao.update(any())).then(invocation -> account);
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

    private String generateValidPat() {
        return SecurityConstants.JWT_TOKEN_PREFIX + validJwtUtils.generatePAT(patToken);
    }

    private String generatePatWithInvalidSignature() {
        JwtUtils invalidKeyJwtUtils = getInvalidKeyJwtUtils();
        return SecurityConstants.JWT_TOKEN_PREFIX + invalidKeyJwtUtils.generatePAT(patToken);
    }

    private SecretKey getValidSecretKey() {
        return Keys.hmacShaKeyFor(configuration.getJwt().getSecretKey().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @see JwtUtils#generateToken(UserAccount, Collection)
     * @return JWT token for {@link #staticUser} with expiration in the past
     */
    private String generateExpiredJwt() {
        final Instant issued = Instant.now().minus(5, ChronoUnit.DAYS);
        final String token = Jwts.builder().setSubject(staticUser.getUsername())
                                 .setIssuedAt(Date.from(issued))
                                 .setExpiration(Date.from(issued.plus(1, ChronoUnit.DAYS)))
                                 .signWith(getValidSecretKey(), SIGNATURE_ALGORITHM)
                                 .serializeToJsonWith(new JacksonSerializer<>(objectMapper))
                                 .compact();
        return SecurityConstants.JWT_TOKEN_PREFIX + token;
    }

    /**
     * @see JwtUtils#generatePAT(PersonalAccessToken)
     * @return PAT JWT token for {@link #staticUser} from {@link #patToken} with expiration in the past.
     */
    private String generateExpiredPat() {
        final Instant issued = Instant.now().minus(5, ChronoUnit.DAYS);
        final String type = Constants.MediaType.JWT_ACCESS_TOKEN;
        final Date expiration = Date.from(issued.plus(1, ChronoUnit.DAYS));
        return Jwts.builder().setSubject(patToken.getUri().toString())
                   .setIssuedAt(Date.from(issued))
                   .setExpiration(expiration)
                   .setHeaderParam(JoseHeaderNames.TYP,  type)
                   .claim(JoseHeaderNames.TYP, type)
                   .signWith(getValidSecretKey(), SIGNATURE_ALGORITHM)
                   .serializeToJsonWith(new JacksonSerializer<>(objectMapper))
                   .compact();
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

    private String requestPat(String userJWT) throws Exception {
        MvcResult result = mockMvc.perform(post(Constants.REST_MAPPING_PATH + PersonalAccessTokenController.PATH)
                                          .header(HttpHeaders.AUTHORIZATION, userJWT))
                .andExpect(status().isOk())
                .andReturn();
        String patToken = result.getResponse().getContentAsString();
        assertFalse(Utils.isBlank(patToken));
        return SecurityConstants.JWT_TOKEN_PREFIX + patToken;
    }

    /**
     * Login endpoint provides valid user JWT on successful authentication.
     */
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

    /**
     * Validates that create PAT rest endpoint
     * generates new valid token which works for authentication of the owner.
     */
    @Test
    void createPatGeneratesValidPat() throws Exception {
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
        final String pat = requestPat(userJWT);

        MvcResult result = makeRequestWithJwt(pat)
                .andExpect(status().isOk())
                .andReturn();

        final UserAccount currentUser = objectMapper.readValue(result.getResponse().getContentAsString(), UserAccount.class);

        assertEquals(staticUser, currentUser);
    }

    @Test
    void patWithInvalidSignatureIsRejected() throws Exception {
        makeRequestWithJwt(generatePatWithInvalidSignature())
                .andExpect(status().isUnauthorized());
    }

    @Test
    void patWithValidSignatureIsAccepted() throws Exception {
        String validPat = generateValidPat();
        when(patDao.find(patToken.getUri())).thenReturn(Optional.of(patToken));
        makeRequestWithJwt(validPat)
                .andExpect(status().isOk());
    }

    @Test
    void expiredJwtIsRejected() throws Exception {
        makeRequestWithJwt(generateExpiredJwt())
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredPatJwtIsRejected() throws Exception {
        makeRequestWithJwt(generateExpiredPat())
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validPatJwtForExpiredPatIsRejected() throws Exception {
        final String patJwt = generateValidPat();
        patToken.setExpirationDate(LocalDate.now().minusDays(2));
        makeRequestWithJwt(patJwt)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validPatJwtForNonExistingPatIsRejected() throws Exception {
        PersonalAccessToken token = new PersonalAccessToken();
        token.setUri(Generator.generateUri());
        token.setOwner(staticUser);
        // token not known to the application
        final String patJwt = SecurityConstants.JWT_TOKEN_PREFIX + validJwtUtils.generatePAT(token);
        makeRequestWithJwt(patJwt)
                .andExpect(status().isUnauthorized());
    }

    static UserAccount customizeAccount(Consumer<UserAccount> customizer) {
        UserAccount account = Generator.generateUserAccount();
        customizer.accept(account);
        return account;
    }

    static Stream<UserAccount> invalidUserAccountSource() {
        return Stream.of(
                customizeAccount(UserAccount::lock),
                customizeAccount(UserAccount::disable)
        );
    }
    
    @ParameterizedTest
    @MethodSource("invalidUserAccountSource")
    void validJwtAndPatAreRejectedForInvalidUserAccount(UserAccount userAccount) throws Exception {
        setUserToContext(userAccount);
        final String token = SecurityConstants.JWT_TOKEN_PREFIX + validJwtUtils.generateToken(userAccount, List.of());
        makeRequestWithJwt(token)
                .andExpect(status().isUnauthorized());
    }
}
