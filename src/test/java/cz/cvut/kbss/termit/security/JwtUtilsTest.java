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

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.environment.config.TestConfig;
import cz.cvut.kbss.termit.exception.IncompleteJwtException;
import cz.cvut.kbss.termit.exception.JwtException;
import cz.cvut.kbss.termit.exception.TokenExpiredException;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.security.model.TermItUserDetails;
import cz.cvut.kbss.termit.util.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static cz.cvut.kbss.termit.security.model.TermItUserDetails.DEFAULT_AUTHORITY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("security")
@ExtendWith({SpringExtension.class, MockitoExtension.class})
@ContextConfiguration(classes = {TestConfig.class}, initializers = {ConfigDataApplicationContextInitializer.class})
class JwtUtilsTest {

    private static final List<String> ROLES = Arrays.asList("USER", "ADMIN");

    @Autowired
    private Configuration config;

    private UserAccount user;

    private SecretKey key;

    private JwtUtils sut;

    @BeforeEach
    void setUp() {
        this.user = Generator.generateUserAccount();
        this.sut = new JwtUtils(config);
        this.key = new SecretKeySpec(config.getJwt().getSecretKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    private String sign(JWTClaimsSet claims) {
        return sign(claims, key);
    }

    private String sign(JWTClaimsSet claims, SecretKey signingKey) {
        try {
            final SignedJWT jwt = new SignedJWT(new JWSHeader(JwtUtils.SIGNATURE_ALGORITHM), claims);
            jwt.sign(new MACSigner(signingKey));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void constructorInitializesKeyWithRandomSecretWhenNoneIsConfigured() {
        final Configuration localConfig = new Configuration();
        localConfig.getJwt().setSecretKey("");
        this.sut = new JwtUtils(localConfig);
        final String jwtToken = sut.generateToken(user, Collections.emptyList());
        assertNotNull(jwtToken);
    }

    @Test
    void generateTokenCreatesJwtForUserWithoutAuthorities() throws Exception {
        final Collection<? extends GrantedAuthority> authorities = Collections.singleton(DEFAULT_AUTHORITY);
        final String jwtToken = sut.generateToken(user, authorities);
        verifyJWToken(jwtToken, user, authorities);
    }

    private void verifyJWToken(String token, UserAccount user, Collection<? extends GrantedAuthority> authorities)
            throws Exception {
        final SignedJWT signedJWT = SignedJWT.parse(token);
        assertTrue(signedJWT.verify(new MACVerifier(key)));
        final JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
        assertEquals(user.getUsername(), claims.getSubject());
        assertThat(claims.getExpirationTime(), greaterThan(claims.getIssueTime()));
        if (!authorities.isEmpty()) {
            assertNotNull(claims.getStringClaim(SecurityConstants.JWT_ROLE_CLAIM));
            final String[] roles = claims.getStringClaim(SecurityConstants.JWT_ROLE_CLAIM)
                                         .split(SecurityConstants.JWT_ROLE_DELIMITER);
            for (String role : roles) {
                assertTrue(authorities.contains(new SimpleGrantedAuthority(role)));
            }
        }
    }

    @Test
    void generateTokenCreatesJwtForUserWithAuthorities() throws Exception {
        final Set<GrantedAuthority> authorities = ROLES.stream().map(SimpleGrantedAuthority::new)
                                                       .collect(Collectors.toSet());
        final String jwtToken = sut.generateToken(user, authorities);
        verifyJWToken(jwtToken, user, authorities);
    }

    @Test
    void extractUserInfoExtractsDataOfUserWithoutAuthoritiesFromJWT() {
        final String token = sign(new JWTClaimsSet.Builder().subject(user.getUsername())
                                                            .jwtID(user.getUri().toString())
                                                            .issueTime(new Date())
                                                            .expirationTime(new Date(System.currentTimeMillis() + SecurityConstants.SESSION_TIMEOUT))
                                                            .build());

        final TermItUserDetails result = sut.extractUserInfo(token);
        assertEquals(user, result.getUser());
        assertEquals(1, result.getAuthorities().size());
        assertTrue(result.getAuthorities().contains(DEFAULT_AUTHORITY));
    }

    @Test
    void extractUserInfoExtractsDataOfUserWithAuthoritiesFromJWT() {
        final String token = sign(new JWTClaimsSet.Builder().subject(user.getUsername())
                                                            .jwtID(user.getUri().toString())
                                                            .issueTime(new Date())
                                                            .expirationTime(new Date(System.currentTimeMillis() + SecurityConstants.SESSION_TIMEOUT))
                                                            .claim(SecurityConstants.JWT_ROLE_CLAIM,
                                                                   String.join(SecurityConstants.JWT_ROLE_DELIMITER, ROLES))
                                                            .build());

        final TermItUserDetails result = sut.extractUserInfo(token);
        ROLES.forEach(r -> assertTrue(result.getAuthorities().contains(new SimpleGrantedAuthority(r))));
    }

    @Test
    void extractUserInfoThrowsJwtExceptionWhenTokenCannotBeParsed() {
        final String token = "bblablalbla";
        final JwtException ex = assertThrows(JwtException.class, () -> sut.extractUserInfo(token));
        assertThat(ex.getMessage(), containsString("Unable to parse the specified JWT."));
    }

    @Test
    void extractUserInfoThrowsJwtExceptionWhenUserIdentifierIsNotValidUri() {
        final String token = sign(new JWTClaimsSet.Builder().subject(user.getUsername())
                                                            .jwtID("_:123")
                                                            .issueTime(new Date())
                                                            .expirationTime(new Date(System.currentTimeMillis() + SecurityConstants.SESSION_TIMEOUT))
                                                            .build());
        assertThrows(JwtException.class, () -> sut.extractUserInfo(token));
    }

    @Test
    void extractUserInfoThrowsIncompleteJwtExceptionWhenUsernameIsMissing() {
        final String token = sign(new JWTClaimsSet.Builder().jwtID(user.getUri().toString())
                                                            .issueTime(new Date())
                                                            .expirationTime(new Date(System.currentTimeMillis() + SecurityConstants.SESSION_TIMEOUT))
                                                            .build());
        final IncompleteJwtException ex = assertThrows(IncompleteJwtException.class, () -> sut.extractUserInfo(token));
        assertThat(ex.getMessage(), containsString("subject"));
    }

    @Test
    void extractUserInfoThrowsIncompleteJwtExceptionWhenIdentifierIsMissing() {
        final String token = sign(new JWTClaimsSet.Builder().subject(user.getUsername())
                                                            .issueTime(new Date())
                                                            .expirationTime(new Date(System.currentTimeMillis() + SecurityConstants.SESSION_TIMEOUT))
                                                            .build());
        final IncompleteJwtException ex = assertThrows(IncompleteJwtException.class, () -> sut.extractUserInfo(token));
        assertThat(ex.getMessage(), containsString("id"));
    }

    @Test
    void extractUserInfoThrowsTokenExpiredExceptionWhenExpirationIsInPast() {
        final String token = sign(new JWTClaimsSet.Builder().jwtID(user.getUri().toString())
                                                            .subject(user.getUsername())
                                                            .issueTime(new Date())
                                                            .expirationTime(new Date(System.currentTimeMillis() - 1000))
                                                            .build());
        assertThrows(TokenExpiredException.class, () -> sut.extractUserInfo(token));
    }

    @Test
    void extractUserInfoThrowsTokenExpiredExceptionWhenExpirationIsMissing() {
        final String token = sign(new JWTClaimsSet.Builder().jwtID(user.getUri().toString())
                                                            .subject(user.getUsername())
                                                            .issueTime(new Date())
                                                            .build());
        assertThrows(TokenExpiredException.class, () -> sut.extractUserInfo(token));
    }

    @Test
    void refreshTokenUpdatesIssuedDate() throws Exception {
        final Date oldIssueDate = new Date(System.currentTimeMillis() - 10000);
        final String token = sign(new JWTClaimsSet.Builder().subject(user.getUsername())
                                                            .jwtID(user.getUri().toString())
                                                            .issueTime(oldIssueDate)
                                                            .expirationTime(new Date(oldIssueDate.getTime() + SecurityConstants.SESSION_TIMEOUT))
                                                            .build());

        final String result = sut.refreshToken(token);
        final SignedJWT signedJWT = SignedJWT.parse(result);
        assertTrue(signedJWT.verify(new MACVerifier(key)));
        final JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
        assertTrue(claims.getIssueTime().after(oldIssueDate));
    }

    @Test
    void refreshTokenUpdatesExpirationDate() throws Exception {
        final Date oldIssueDate = new Date();
        final Date oldExpiration = new Date(oldIssueDate.getTime() + 10000);
        final String token = sign(new JWTClaimsSet.Builder().subject(user.getUsername())
                                                            .jwtID(user.getUri().toString())
                                                            .issueTime(oldIssueDate)
                                                            .expirationTime(oldExpiration)
                                                            .build());

        final String result = sut.refreshToken(token);
        final SignedJWT signedJWT = SignedJWT.parse(result);
        assertTrue(signedJWT.verify(new MACVerifier(key)));
        final JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
        assertTrue(claims.getExpirationTime().after(oldExpiration));
    }

    @Test
    void extractUserInfoThrowsJwtExceptionWhenTokenIsSignedWithInvalidSecret() {
        final String token = sign(new JWTClaimsSet.Builder().subject(user.getUsername())
                                                            .jwtID(user.getUri().toString())
                                                            .issueTime(new Date())
                                                            .expirationTime(new Date(System.currentTimeMillis() + SecurityConstants.SESSION_TIMEOUT))
                                                            .build(),
                                  new SecretKeySpec("differentSecretKeyThatIsAlsoLongEnough".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));

        assertThrows(JwtException.class, () -> sut.extractUserInfo(token));
    }
}
