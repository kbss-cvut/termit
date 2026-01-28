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
import cz.cvut.kbss.termit.exception.IncompleteJwtException;
import cz.cvut.kbss.termit.exception.JwtException;
import cz.cvut.kbss.termit.exception.TokenExpiredException;
import cz.cvut.kbss.termit.model.PersonalAccessToken;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.security.model.TermItUserDetails;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.Utils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.jackson.io.JacksonDeserializer;
import io.jsonwebtoken.jackson.io.JacksonSerializer;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import jakarta.annotation.Nonnull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JoseHeaderNames;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class JwtUtils {

    static final SignatureAlgorithm SIGNATURE_ALGORITHM =
            SignatureAlgorithm.forName(SecurityConstants.JWT_DEFAULT_KEY_ALGORITHM);

    private final ObjectMapper objectMapper;

    private final JwtParser jwtParser;

    private final SecretKey key;

    @Autowired
    public JwtUtils(@Qualifier("objectMapper") ObjectMapper objectMapper, Configuration config) {
        this.objectMapper = objectMapper;
        this.key = Utils.isBlank(config.getJwt().getSecretKey()) ? Keys.secretKeyFor(SIGNATURE_ALGORITHM) :
                         Keys.hmacShaKeyFor(config.getJwt().getSecretKey().getBytes(StandardCharsets.UTF_8));

        this.jwtParser = Jwts.parserBuilder().setSigningKey(key)
                             .deserializeJsonWith(new JacksonDeserializer<>(objectMapper))
                             .build();
    }

    public SecretKey getJwtSigningKey() {
        return key;
    }

    /**
     * Generates a JSON Web Token for the specified authenticated user.
     *
     * @param user User info
     * @return Generated JWT hash
     */
    public String generateToken(UserAccount user, Collection<? extends GrantedAuthority> authorities) {
        return prebuildJwt(user.getUsername(), user.getUri(), authorities, null)
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
            final Claims claims = getClaimsFromToken(token).getBody();
            return extractUserInfo(claims);
        } catch (IllegalArgumentException e) {
            throw new JwtException("Unable to parse user identifier from the specified JWT.", e);
        }
    }

    public TermItUserDetails extractUserInfo(final @Nonnull Claims claims) {
        Objects.requireNonNull(claims);
        try {
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

    public Jws<Claims> getClaimsFromToken(String token) {
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

    private Jws<Claims> parseClaims(String token) {
        return jwtParser.parseClaimsJws(token);
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

    public static List<GrantedAuthority> mapClaimToAuthorities(String claim) {
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
        final Claims claims = getClaimsFromToken(token).getBody();
        final Instant issued = issueTimestamp();
        claims.setIssuedAt(Date.from(issued));
        claims.setExpiration(Date.from(issued.plusMillis(SecurityConstants.SESSION_TIMEOUT)));
        return Jwts.builder().setClaims(claims)
                   .signWith(key, SIGNATURE_ALGORITHM)
                   .serializeToJsonWith(new JacksonSerializer<>(objectMapper))
                   .compact();
    }

    /**
     * Builds common JWT parts.
     * Generate the string token with {@link JwtBuilder#compact()}.
     * @param subject The subject claim
     * @param expiration The token expiration or null to use default session length
     * @return JwtBuilder with common parts set
     */
    private JwtBuilder prebuildJwt(String subject, URI userId, Collection<? extends GrantedAuthority> authorities, Date expiration) {
        final Instant issued = issueTimestamp();
        if (expiration == null) {
            expiration = Date.from(issued.plusMillis(SecurityConstants.SESSION_TIMEOUT));
        }
        return Jwts.builder().setSubject(subject)
                   .setIssuedAt(Date.from(issued))
                   .setExpiration(expiration)
                   .claim(SecurityConstants.JWT_ROLE_CLAIM, mapAuthoritiesToClaim(authorities))
                   .claim(JwtClaimNames.JTI, userId.toString())
                   .signWith(key, SIGNATURE_ALGORITHM)
                   .serializeToJsonWith(new JacksonSerializer<>(objectMapper));
    }

    /**
     * Generates Access Token JWT.
     * @param newToken The token to generate
     * @return The token value
     */
    public String generatePAT(PersonalAccessToken newToken) {
        final String type = Constants.MediaType.JWT_ACCESS_TOKEN;
        Date expiration = new Date(Long.MAX_VALUE);
        if (newToken.getExpirationDate() != null) {
            expiration = Date.from(newToken.getExpirationDate().atStartOfDay(ZoneId.systemDefault()).toInstant());
        }
        return prebuildJwt(newToken.getUri().toString(), newToken.getOwner().getUri(), List.of(), expiration)
                .setHeaderParam(JoseHeaderNames.TYP,  type)
                .compact();
    }
}
