/**
 * TermIt Copyright (C) 2019 Czech Technical University in Prague
 * <p>
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with this program.  If not, see
 * <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import cz.cvut.kbss.termit.exception.IncompleteJwtException;
import cz.cvut.kbss.termit.exception.JwtException;
import cz.cvut.kbss.termit.exception.TokenExpiredException;
import cz.cvut.kbss.termit.model.AbstractUser;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.security.model.TermItUserDetails;
import cz.cvut.kbss.termit.util.Configuration;
import io.jsonwebtoken.*;
import io.jsonwebtoken.jackson.io.JacksonDeserializer;
import io.jsonwebtoken.jackson.io.JacksonSerializer;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class JwtUtils {

    static final SignatureAlgorithm SIGNATURE_ALGORITHM = SignatureAlgorithm.HS256;

    private final ObjectMapper objectMapper;

    private final Key key;

    @Autowired
    public JwtUtils(@Qualifier("objectMapper") ObjectMapper objectMapper, Configuration config) {
        this.objectMapper = objectMapper;
        this.key = Keys.hmacShaKeyFor(config.getJwt().getSecretKey().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generates a JSON Web Token for the specified authenticated user.
     *
     * @param user User info
     * @return Generated JWT hash
     */
    public String generateToken(AbstractUser user, Collection<? extends GrantedAuthority> authorities) {
        final Instant issued = issueTimestamp();
        return Jwts.builder().setSubject(user.getUsername())
                   .setId(user.getUri().toString())
                   .setIssuedAt(Date.from(issued))
                   .setExpiration(Date.from(issued.plusMillis(SecurityConstants.SESSION_TIMEOUT)))
                   .claim(SecurityConstants.JWT_ROLE_CLAIM, mapAuthoritiesToClaim(authorities))
                   .signWith(key, SIGNATURE_ALGORITHM)
                   .serializeToJsonWith(new JacksonSerializer<>(objectMapper))
                   .compact();
    }

    static Instant issueTimestamp() {
        // Truncate timestamp to seconds, it would get truncated on serialization/deserialization anyway
        return Instant.now().truncatedTo(ChronoUnit.SECONDS);
    }

    private static String mapAuthoritiesToClaim(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream().map(GrantedAuthority::getAuthority)
                          .collect(Collectors.joining(SecurityConstants.JWT_ROLE_DELIMITER));
    }

    /**
     * Retrieves user info from the specified JWT.
     * <p>
     * The token is first validated for correct format and expiration date.
     *
     * @param token JWT to read
     * @return User info retrieved from the specified token
     */
    public TermItUserDetails extractUserInfo(String token) {
        Objects.requireNonNull(token);
        try {
            final Claims claims = getClaimsFromToken(token);
            verifyAttributePresence(claims);
            final UserAccount user = new UserAccount();
            user.setUri(URI.create(claims.getId()));
            user.setUsername(claims.getSubject());
            final String roles = claims.get(SecurityConstants.JWT_ROLE_CLAIM, String.class);
            return new TermItUserDetails(user, mapClaimToAuthorities(roles));
        } catch (IllegalArgumentException e) {
            throw new JwtException("Unable to parse user identifier from the specified JWT.", e);
        }
    }

    private Claims getClaimsFromToken(String token) {
        try {
            return parseClaims(token);
        } catch (MalformedJwtException | UnsupportedJwtException e) {
            throw new JwtException("Unable to parse the specified JWT.", e);
        } catch (SecurityException e) {
            throw new JwtException("Invalid signature of the specified JWT.", e);
        } catch (ExpiredJwtException e) {
            throw new TokenExpiredException(e.getMessage());
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder().setSigningKey(key)
                   .deserializeJsonWith(new JacksonDeserializer<>(objectMapper))
                   .build().parseClaimsJws(token).getBody();
    }

    private static void verifyAttributePresence(Claims claims) {
        if (claims.getSubject() == null) {
            throw new IncompleteJwtException("JWT is missing subject.");
        }
        if (claims.getId() == null) {
            throw new IncompleteJwtException("JWT is missing id.");
        }
        if (claims.getExpiration() == null) {
            throw new TokenExpiredException("Missing token expiration info. Assuming expired.");
        }
    }

    private static List<GrantedAuthority> mapClaimToAuthorities(String claim) {
        if (claim == null) {
            return Collections.emptyList();
        }
        final String[] roles = claim.split(SecurityConstants.JWT_ROLE_DELIMITER);
        final List<GrantedAuthority> authorities = new ArrayList<>(roles.length);
        for (String role : roles) {
            authorities.add(new SimpleGrantedAuthority(role));
        }
        return authorities;
    }

    /**
     * Updates issuing and expiration date of the specified token, generating a new one.
     *
     * @param token The token to refresh
     * @return Newly generated token with updated expiration date
     */
    public String refreshToken(String token) {
        Objects.requireNonNull(token);
        final Claims claims = getClaimsFromToken(token);
        final Instant issued = issueTimestamp();
        claims.setIssuedAt(Date.from(issued));
        claims.setExpiration(Date.from(issued.plusMillis(SecurityConstants.SESSION_TIMEOUT)));
        return Jwts.builder().setClaims(claims)
                   .signWith(key, SIGNATURE_ALGORITHM)
                   .serializeToJsonWith(new JacksonSerializer<>(objectMapper))
                   .compact();
    }

    /**
     * Gets URI of the user represented by the specified token.
     *
     * Note that it is expected
     * @param token JSON Web Token
     * @return User identifier
     */
    public URI getUserUri(String token) {
        try {
            final Claims claims = parseClaims(token);
            return URI.create(claims.getId());
        } catch (ExpiredJwtException e) {
            return URI.create(e.getClaims().getId());
        }
    }

    /**
     * Retrieves timestamp when the specified token was issued.
     *
     * @param token JSON Web Token
     * @return Timestamp of token issue
     */
    public Instant getTokenIssueTimestamp(String token) {
        try {
            final Claims claims = parseClaims(token);
            return claims.getIssuedAt().toInstant();
        } catch (ExpiredJwtException e) {
            return e.getClaims().getIssuedAt().toInstant();
        }
    }
}
