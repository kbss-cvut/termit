/*
 * TermIt
 * Copyright (C) 2023 Czech Technical University in Prague
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

import cz.cvut.kbss.termit.environment.Environment;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.environment.config.TestConfig;
import cz.cvut.kbss.termit.exception.IncompleteJwtException;
import cz.cvut.kbss.termit.exception.JwtException;
import cz.cvut.kbss.termit.exception.TokenExpiredException;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.security.model.TermItUserDetails;
import cz.cvut.kbss.termit.util.Configuration;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.*;
import java.util.stream.Collectors;

import static cz.cvut.kbss.termit.security.model.TermItUserDetails.DEFAULT_AUTHORITY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.*;

@Tag("security")
@ExtendWith({SpringExtension.class, MockitoExtension.class})
@ContextConfiguration(classes = {TestConfig.class}, initializers = {ConfigDataApplicationContextInitializer.class})
class JwtUtilsTest {

    private static final List<String> ROLES = Arrays.asList("USER", "ADMIN");

    @Autowired
    private Configuration config;

    private UserAccount user;

    private Key key;

    private JwtUtils sut;

    @BeforeEach
    void setUp() {
        this.user = Generator.generateUserAccount();
        this.sut = new JwtUtils(Environment.getObjectMapper(), config);
        this.key = Keys.hmacShaKeyFor(config.getJwt().getSecretKey().getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void constructorInitializesKeyWithRandomSecretWhenNoneIsConfigured() {
        final Configuration localConfig = new Configuration();
        localConfig.getJwt().setSecretKey("");
        this.sut = new JwtUtils(Environment.getObjectMapper(), localConfig);
        final String jwtToken = sut.generateToken(user, Collections.emptyList());
        assertNotNull(jwtToken);
    }

    @Test
    void generateTokenCreatesJwtForUserWithoutAuthorities() {
        final Collection<? extends GrantedAuthority> authorities = Collections.singleton(DEFAULT_AUTHORITY);
        final String jwtToken = sut.generateToken(user, authorities);
        verifyJWToken(jwtToken, user, authorities);
    }

    private void verifyJWToken(String token, UserAccount user, Collection<? extends GrantedAuthority> authorities) {
        final Claims claims = Jwts.parserBuilder().setSigningKey(Keys.hmacShaKeyFor(config.getJwt().getSecretKey()
                                                                                          .getBytes(
                                                                                                  StandardCharsets.UTF_8)))
                                  .build()
                                  .parseClaimsJws(token)
                                  .getBody();
        assertEquals(user.getUsername(), claims.getSubject());
        assertEquals(user.getUri().toString(), claims.getId());
        assertThat(claims.getExpiration(), greaterThan(claims.getIssuedAt()));
        if (!authorities.isEmpty()) {
            assertTrue(claims.containsKey(SecurityConstants.JWT_ROLE_CLAIM));
            final String[] roles = claims.get(SecurityConstants.JWT_ROLE_CLAIM, String.class)
                                         .split(SecurityConstants.JWT_ROLE_DELIMITER);
            for (String role : roles) {
                assertTrue(authorities.contains(new SimpleGrantedAuthority(role)));
            }
        }
    }

    @Test
    void generateTokenCreatesJwtForUserWithAuthorities() {
        final Set<GrantedAuthority> authorities = ROLES.stream().map(SimpleGrantedAuthority::new)
                                                       .collect(Collectors.toSet());
        final String jwtToken = sut.generateToken(user, authorities);
        verifyJWToken(jwtToken, user, authorities);
    }

    @Test
    void extractUserInfoExtractsDataOfUserWithoutAuthoritiesFromJWT() {
        final String token = Jwts.builder().setSubject(user.getUsername())
                                 .setId(user.getUri().toString())
                                 .setIssuedAt(new Date())
                                 .setExpiration(
                                         new Date(System.currentTimeMillis() + SecurityConstants.SESSION_TIMEOUT))
                                 .signWith(key, JwtUtils.SIGNATURE_ALGORITHM).compact();

        final TermItUserDetails result = sut.extractUserInfo(token);
        assertEquals(user, result.getUser());
        assertEquals(1, result.getAuthorities().size());
        assertTrue(result.getAuthorities().contains(DEFAULT_AUTHORITY));
    }

    @Test
    void extractUserInfoExtractsDataOfUserWithAuthoritiesFromJWT() {
        final String token = Jwts.builder().setSubject(user.getUsername())
                                 .setId(user.getUri().toString())
                                 .setIssuedAt(new Date())
                                 .setExpiration(
                                         new Date(System.currentTimeMillis() + SecurityConstants.SESSION_TIMEOUT))
                                 .claim(SecurityConstants.JWT_ROLE_CLAIM,
                                        String.join(SecurityConstants.JWT_ROLE_DELIMITER, ROLES))
                                 .signWith(key, JwtUtils.SIGNATURE_ALGORITHM).compact();

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
        final String token = Jwts.builder().setSubject(user.getUsername())
                                 .setId("_:123")
                                 .setIssuedAt(new Date())
                                 .setExpiration(
                                         new Date(System.currentTimeMillis() + SecurityConstants.SESSION_TIMEOUT))
                                 .signWith(key, JwtUtils.SIGNATURE_ALGORITHM).compact();
        assertThrows(JwtException.class, () -> sut.extractUserInfo(token));
    }

    @Test
    void extractUserInfoThrowsIncompleteJwtExceptionWhenUsernameIsMissing() {
        final String token = Jwts.builder().setId(user.getUri().toString())
                                 .setIssuedAt(new Date())
                                 .setExpiration(
                                         new Date(System.currentTimeMillis() + SecurityConstants.SESSION_TIMEOUT))
                                 .signWith(key, JwtUtils.SIGNATURE_ALGORITHM).compact();
        final IncompleteJwtException ex = assertThrows(IncompleteJwtException.class, () -> sut.extractUserInfo(token));
        assertThat(ex.getMessage(), containsString("subject"));
    }

    @Test
    void extractUserInfoThrowsIncompleteJwtExceptionWhenIdentifierIsMissing() {
        final String token = Jwts.builder().setSubject(user.getUsername())
                                 .setIssuedAt(new Date())
                                 .setExpiration(
                                         new Date(System.currentTimeMillis() + SecurityConstants.SESSION_TIMEOUT))
                                 .signWith(key, JwtUtils.SIGNATURE_ALGORITHM).compact();
        final IncompleteJwtException ex = assertThrows(IncompleteJwtException.class, () -> sut.extractUserInfo(token));
        assertThat(ex.getMessage(), containsString("id"));
    }

    @Test
    void extractUserInfoThrowsTokenExpiredExceptionWhenExpirationIsInPast() {
        final String token = Jwts.builder().setId(user.getUri().toString())
                                 .setSubject(user.getUsername())
                                 .setIssuedAt(new Date())
                                 .setExpiration(new Date(System.currentTimeMillis() - 1000))
                                 .signWith(key, JwtUtils.SIGNATURE_ALGORITHM).compact();
        assertThrows(TokenExpiredException.class, () -> sut.extractUserInfo(token));
    }

    @Test
    void extractUserInfoThrowsTokenExpiredExceptionWhenExpirationIsMissing() {
        final String token = Jwts.builder().setId(user.getUri().toString())
                                 .setSubject(user.getUsername())
                                 .setIssuedAt(new Date())
                                 .signWith(key, JwtUtils.SIGNATURE_ALGORITHM).compact();
        assertThrows(TokenExpiredException.class, () -> sut.extractUserInfo(token));
    }

    @Test
    void refreshTokenUpdatesIssuedDate() {
        final Date oldIssueDate = new Date(System.currentTimeMillis() - 10000);
        final String token = Jwts.builder().setSubject(user.getUsername())
                                 .setId(user.getUri().toString())
                                 .setIssuedAt(oldIssueDate)
                                 .setExpiration(new Date(oldIssueDate.getTime() + SecurityConstants.SESSION_TIMEOUT))
                                 .signWith(key, JwtUtils.SIGNATURE_ALGORITHM).compact();

        final String result = sut.refreshToken(token);
        final Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(result)
                                  .getBody();
        assertTrue(claims.getIssuedAt().after(oldIssueDate));
    }

    @Test
    void refreshTokenUpdatesExpirationDate() {
        final Date oldIssueDate = new Date();
        final Date oldExpiration = new Date(oldIssueDate.getTime() + 10000);
        final String token = Jwts.builder().setSubject(user.getUsername())
                                 .setId(user.getUri().toString())
                                 .setIssuedAt(oldIssueDate)
                                 .setExpiration(oldExpiration)
                                 .signWith(key, JwtUtils.SIGNATURE_ALGORITHM).compact();

        final String result = sut.refreshToken(token);
        final Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(result)
                                  .getBody();
        assertTrue(claims.getExpiration().after(oldExpiration));
    }

    @Test
    void extractUserInfoThrowsJwtExceptionWhenTokenIsSignedWithInvalidSecret() {
        final String token = Jwts.builder().setSubject(user.getUsername())
                                 .setId(user.getUri().toString())
                                 .setIssuedAt(new Date())
                                 .setExpiration(
                                         new Date(System.currentTimeMillis() + SecurityConstants.SESSION_TIMEOUT))
                                 .signWith(Keys.hmacShaKeyFor(
                                                   "differentSecretKeyThatIsAlsoLongEnough".getBytes(StandardCharsets.UTF_8)),
                                           JwtUtils.SIGNATURE_ALGORITHM)
                                 .compact();

        assertThrows(JwtException.class, () -> sut.extractUserInfo(token));
    }
}
