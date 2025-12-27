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
package cz.cvut.kbss.termit.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import cz.cvut.kbss.termit.config.JwtConfig;
import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.environment.config.TestConfig;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.rest.ConfigurationController;
import cz.cvut.kbss.termit.rest.handler.ErrorInfo;
import cz.cvut.kbss.termit.security.model.TermItUserDetails;
import cz.cvut.kbss.termit.service.security.TermItUserDetailsService;
import cz.cvut.kbss.termit.util.Configuration;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.MappedJwtClaimSetConverter;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static cz.cvut.kbss.termit.security.SecurityConstants.PUBLIC_API_PATH;
import static cz.cvut.kbss.termit.util.Constants.REST_MAPPING_PATH;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("security")
@ExtendWith({SpringExtension.class, MockitoExtension.class})
@ContextConfiguration(classes = {TestConfig.class}, initializers = {ConfigDataApplicationContextInitializer.class})
class JwtAuthorizationFilterTest {

    @Autowired
    private Configuration config;

    private UserAccount user;

    private final MockHttpServletRequest mockRequest = new MockHttpServletRequest();

    private final MockHttpServletResponse mockResponse = new MockHttpServletResponse();

    @Mock
    private FilterChain chainMock;

    @Mock
    private AuthenticationManager authManagerMock;

    @Mock
    private TermItUserDetailsService detailsServiceMock;

    private JwtUtils jwtUtilsSpy;

    private ObjectMapper objectMapper;

    private SecretKey signingKey;

    private JwtAuthorizationFilter sut;

    private final Instant tokenIssued = JwtUtils.issueTimestamp();

    private OAuth2TokenValidator<Jwt> jwtValidator() {
        return new DelegatingOAuth2TokenValidator<>(List.of(
                new JwtTimestampValidator(),
                new JwtUserDetailsValidator()
        ));
    }

    private MappedJwtClaimSetConverter jwtClaimSetConverter() {
        return MappedJwtClaimSetConverter.withDefaults(
                Map.of(Claims.SUBJECT, new UsernameToUserDetailsConverter(detailsServiceMock)));
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        final NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(signingKey)
                                                         .macAlgorithm(MacAlgorithm.HS256)
                                                         .validateType(false)
                                                         .jwtProcessorCustomizer(JwtConfig::setJWSTypeVerifier)
                                                         .build();
        decoder.setJwtValidator(jwtValidator());
        decoder.setClaimSetConverter(jwtClaimSetConverter());
        return decoder;
    }

    @BeforeEach
    void setUp() {
        this.user = Generator.generateUserAccount();
        this.objectMapper = Environment.getObjectMapper();
        this.signingKey = Keys.hmacShaKeyFor(config.getJwt().getSecretKey().getBytes(StandardCharsets.UTF_8));
        this.jwtUtilsSpy = spy(new JwtUtils(objectMapper, config));
        this.sut = new JwtAuthorizationFilter(authManagerMock, jwtUtilsSpy, objectMapper);
    }

    @AfterEach
    void tearDown() {
        Environment.resetCurrentUser();
    }

    @Test
    void doFilterInternalExtractsUserInfoFromJwtAndSetsUpSecurityContext() throws Exception {
        when(detailsServiceMock.loadUserByUsername(user.getUsername())).thenReturn(new TermItUserDetails(user));
        generateJwtIntoRequest();

        sut.doFilterInternal(mockRequest, mockResponse, chainMock);
        assertEquals(user, Environment.getCurrentUser());
    }

    private void generateJwtIntoRequest() {
        final String token = generateJwt();
        mockRequest.addHeader(HttpHeaders.AUTHORIZATION, SecurityConstants.JWT_TOKEN_PREFIX + token);
    }

    private String generateJwt() {
        return Jwts.builder().setSubject(user.getUsername())
                   .setId(user.getUri().toString())
                   .setIssuedAt(Date.from(tokenIssued))
                   .setExpiration(Date.from(tokenIssued.plusMillis(10000L)))
                   .signWith(signingKey, JwtUtils.SIGNATURE_ALGORITHM)
                   .compact();
    }

    @Test
    void doFilterInternalInvokesFilterChainAfterSuccessfulExtractionOfUserInfo() throws Exception {
        when(detailsServiceMock.loadUserByUsername(user.getUsername())).thenReturn(new TermItUserDetails(user));
        generateJwtIntoRequest();
        sut.doFilterInternal(mockRequest, mockResponse, chainMock);
        verify(chainMock).doFilter(mockRequest, mockResponse);
    }

    @Test
    void doFilterInternalLeavesEmptySecurityContextAndPassesRequestDownChainWhenAuthenticationIsMissing()
            throws Exception {
        Environment.resetCurrentUser();
        sut.doFilterInternal(mockRequest, mockResponse, chainMock);
        verify(chainMock).doFilter(mockRequest, mockResponse);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilterInternalLeavesEmptySecurityContextAndPassesRequestDownChainWhenAuthenticationHasIncorrectFormat()
            throws Exception {
        Environment.resetCurrentUser();
        mockRequest.addHeader(HttpHeaders.AUTHORIZATION, generateJwt());
        sut.doFilterInternal(mockRequest, mockResponse, chainMock);
        verify(chainMock).doFilter(mockRequest, mockResponse);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilterInternalRefreshesUserTokenOnSuccessfulAuthorization() throws Exception {
        when(detailsServiceMock.loadUserByUsername(user.getUsername())).thenReturn(new TermItUserDetails(user));
        generateJwtIntoRequest();
        sut.doFilterInternal(mockRequest, mockResponse, chainMock);
        assertTrue(mockResponse.containsHeader(HttpHeaders.AUTHORIZATION));
        assertNotEquals(mockRequest.getHeader(HttpHeaders.AUTHORIZATION),
                        mockResponse.getHeader(HttpHeaders.AUTHORIZATION));
        verify(jwtUtilsSpy).refreshToken(any());
    }

    @Test
    void doFilterInternalReturnsUnauthorizedWhenTokenIsExpired() throws Exception {
        final Instant issued = Instant.now().minusSeconds(1000);
        final Instant expiration = issued.plusSeconds(10);
        final String token = Jwts.builder().setSubject(user.getUsername())
                                 .setId(user.getUri().toString())
                                 .setIssuedAt(Date.from(issued))
                                 .setExpiration(Date.from(expiration))
                                 .signWith(signingKey, JwtUtils.SIGNATURE_ALGORITHM).compact();
        mockRequest.addHeader(HttpHeaders.AUTHORIZATION, SecurityConstants.JWT_TOKEN_PREFIX + token);
        sut.doFilterInternal(mockRequest, mockResponse, chainMock);
        assertEquals(HttpStatus.UNAUTHORIZED.value(), mockResponse.getStatus());
        final ErrorInfo errorInfo = objectMapper.readValue(mockResponse.getContentAsString(), ErrorInfo.class);
        assertNotNull(errorInfo);
        assertThat(errorInfo.getMessage(), containsString("expired"));
    }

    @Test
    void doFilterInternalReturnsUnauthorizedWhenUserAccountIsLocked() throws Exception {
        when(detailsServiceMock.loadUserByUsername(user.getUsername())).thenReturn(new TermItUserDetails(user));
        generateJwtIntoRequest();
        user.lock();
        sut.doFilterInternal(mockRequest, mockResponse, chainMock);
        assertEquals(HttpStatus.UNAUTHORIZED.value(), mockResponse.getStatus());
        final ErrorInfo errorInfo = objectMapper.readValue(mockResponse.getContentAsString(), ErrorInfo.class);
        assertNotNull(errorInfo);
        assertThat(errorInfo.getMessage(), containsString("locked"));
    }

    @Test
    void doFilterInternalReturnsUnauthorizedWhenUserAccountIsDisabled() throws Exception {
        when(detailsServiceMock.loadUserByUsername(user.getUsername())).thenReturn(new TermItUserDetails(user));
        generateJwtIntoRequest();
        user.disable();
        sut.doFilterInternal(mockRequest, mockResponse, chainMock);
        assertEquals(HttpStatus.UNAUTHORIZED.value(), mockResponse.getStatus());
        final ErrorInfo errorInfo = objectMapper.readValue(mockResponse.getContentAsString(), ErrorInfo.class);
        assertNotNull(errorInfo);
        assertThat(errorInfo.getMessage(), containsString("disabled"));
    }

    @Test
    void doFilterInternalReturnsUnauthorizedOnIncompleteJwtToken() throws Exception {
        // Missing id
        final String token = Jwts.builder().setSubject(user.getUsername())
                                 .setIssuedAt(new Date())
                                 .setExpiration(new Date(System.currentTimeMillis() + 10000))
                                 .signWith(signingKey, JwtUtils.SIGNATURE_ALGORITHM).compact();
        mockRequest.addHeader(HttpHeaders.AUTHORIZATION, SecurityConstants.JWT_TOKEN_PREFIX + token);
        sut.doFilterInternal(mockRequest, mockResponse, chainMock);
        assertEquals(HttpStatus.UNAUTHORIZED.value(), mockResponse.getStatus());
        final ErrorInfo errorInfo = objectMapper.readValue(mockResponse.getContentAsString(), ErrorInfo.class);
        assertNotNull(errorInfo);
        assertThat(errorInfo.getMessage(), containsString("Invalid JWT token contents"));
    }

    @Test
    void doFilterInternalReturnsUnauthorizedOnUnparseableUserInfoInJwtToken() throws Exception {
        // Missing id
        final String token = Jwts.builder().setSubject(user.getUsername())
                                 .setId(":1235")    // Not valid URI
                                 .setIssuedAt(new Date())
                                 .setExpiration(new Date(System.currentTimeMillis() + 10000))
                                 .signWith(signingKey, JwtUtils.SIGNATURE_ALGORITHM).compact();
        mockRequest.addHeader(HttpHeaders.AUTHORIZATION, SecurityConstants.JWT_TOKEN_PREFIX + token);
        sut.doFilterInternal(mockRequest, mockResponse, chainMock);
        assertEquals(HttpStatus.UNAUTHORIZED.value(), mockResponse.getStatus());
        final ErrorInfo errorInfo = objectMapper.readValue(mockResponse.getContentAsString(), ErrorInfo.class);
        assertNotNull(errorInfo);
    }

    @Test
    void doFilterInternalReturnsUnauthorizedForUnknownUserInToken() throws Exception {
        final String token = Jwts.builder().setSubject("unknownUser")
                                 .setId(Generator.generateUri().toString())
                                 .setIssuedAt(new Date())
                                 .setExpiration(new Date(System.currentTimeMillis() + 10000))
                                 .signWith(signingKey, JwtUtils.SIGNATURE_ALGORITHM).compact();
        when(detailsServiceMock.loadUserByUsername(anyString())).thenThrow(UsernameNotFoundException.class);
        mockRequest.addHeader(HttpHeaders.AUTHORIZATION, SecurityConstants.JWT_TOKEN_PREFIX + token);
        sut.doFilterInternal(mockRequest, mockResponse, chainMock);
        assertEquals(HttpStatus.UNAUTHORIZED.value(), mockResponse.getStatus());
    }

    @Test
    void shouldNotFilterReturnsTrueForRequestToPublicApi() {
        assertFalse(sut.shouldNotFilter(mockRequest));
        mockRequest.setRequestURI("/termit" + REST_MAPPING_PATH + PUBLIC_API_PATH + "/vocabularies");
        assertTrue(sut.shouldNotFilter(mockRequest));
    }

    @Test
    void doFilterInternalAllowsRequestThroughWhenTokenIsExpiredAndTargetIsConfiguration() throws Exception {
        mockRequest.setRequestURI("/termit" + REST_MAPPING_PATH + ConfigurationController.PATH);
        final String token = Jwts.builder().setSubject(user.getUsername())
                                 .setId(user.getUri().toString())
                                 .setIssuedAt(new Date())
                                 .setExpiration(new Date(System.currentTimeMillis() - 10000))
                                 .signWith(signingKey, JwtUtils.SIGNATURE_ALGORITHM).compact();
        mockRequest.addHeader(HttpHeaders.AUTHORIZATION, SecurityConstants.JWT_TOKEN_PREFIX + token);
        sut.doFilterInternal(mockRequest, mockResponse, chainMock);
        verify(chainMock).doFilter(mockRequest, mockResponse);
    }
}
