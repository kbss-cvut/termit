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
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import cz.cvut.kbss.termit.exception.IncompleteJwtException;
import cz.cvut.kbss.termit.exception.JwtException;
import cz.cvut.kbss.termit.exception.TermItException;
import cz.cvut.kbss.termit.exception.TokenExpiredException;
import cz.cvut.kbss.termit.model.PersonalAccessToken;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.security.model.TermItUserDetails;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;
import cz.cvut.kbss.termit.util.Utils;
import jakarta.annotation.Nonnull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
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

    static final JWSAlgorithm SIGNATURE_ALGORITHM = JWSAlgorithm.HS256;

    private final SecretKey key;

    private final JWSSigner signer;

    private final JWSVerifier verifier;

    @Autowired
    public JwtUtils(Configuration config) {
        this.key = initSigningKey(config);
        try {
            this.signer = new MACSigner(key);
            this.verifier = new MACVerifier(key);
        } catch (JOSEException e) {
            throw new TermItException("Unable to initialize JWT signing/verification.", e);
        }
    }

    private static SecretKey initSigningKey(Configuration config) {
        if (Utils.isBlank(config.getJwt().getSecretKey())) {
            try {
                final KeyGenerator keyGenerator = KeyGenerator.getInstance("HmacSHA256");
                keyGenerator.init(256);
                return keyGenerator.generateKey();
            } catch (NoSuchAlgorithmException e) {
                throw new TermItException("Unable to generate JWT signing key.", e);
            }
        }
        return new SecretKeySpec(config.getJwt().getSecretKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
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
        final JWTClaimsSet claims = prebuildClaims(user.getUsername(), user.getUri(), authorities, null).build();
        return sign(claims, null);
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
        return extractUserInfo(getClaimsFromToken(token));
    }

    public TermItUserDetails extractUserInfo(final @Nonnull JWTClaimsSet claims) {
        Objects.requireNonNull(claims);
        verifyAttributePresence(claims);
        try {
            final UserAccount user = new UserAccount();
            user.setUri(URI.create(claims.getJWTID()));
            user.setUsername(claims.getSubject());
            final String roles = claims.getStringClaim(SecurityConstants.JWT_ROLE_CLAIM);
            return new TermItUserDetails(user, mapClaimToAuthorities(roles));
        } catch (IllegalArgumentException | ParseException e) {
            throw new JwtException("Unable to parse user identifier from the specified JWT.", e);
        }
    }

    /**
     * Parses the specified compact JWT and verifies its signature.
     *
     * @param token JWT to parse
     * @return Parsed claim set
     */
    public JWTClaimsSet getClaimsFromToken(String token) {
        try {
            final SignedJWT signedJWT = SignedJWT.parse(token);
            if (!signedJWT.verify(verifier)) {
                throw new JwtException("Invalid signature of the specified JWT.");
            }
            return signedJWT.getJWTClaimsSet();
        } catch (ParseException e) {
            throw new JwtException("Unable to parse the specified JWT.", e);
        } catch (JOSEException e) {
            throw new JwtException("Invalid signature of the specified JWT.", e);
        }
    }

    private static void verifyAttributePresence(JWTClaimsSet claims) {
        if (claims.getSubject() == null) {
            throw new IncompleteJwtException("JWT is missing subject.");
        }
        if (claims.getJWTID() == null) {
            throw new IncompleteJwtException("JWT is missing id.");
        }
        if (claims.getExpirationTime() == null) {
            throw new TokenExpiredException("Missing token expiration info. Assuming expired.");
        }
        if (claims.getExpirationTime().before(new Date())) {
            throw new TokenExpiredException("The specified JWT has expired.");
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
        final JWTClaimsSet claims = getClaimsFromToken(token);
        final Instant issued = issueTimestamp();
        final JWTClaimsSet refreshed = new JWTClaimsSet.Builder(claims)
                .issueTime(Date.from(issued))
                .expirationTime(Date.from(issued.plusMillis(SecurityConstants.SESSION_TIMEOUT)))
                .build();
        return sign(refreshed, null);
    }

    /**
     * Builds common JWT claims. Produce the compact token with {@link #sign(JWTClaimsSet, JOSEObjectType)}.
     *
     * @param subject     The subject claim
     * @param userId      User identifier stored in the {@code jti} claim
     * @param authorities User authorities stored in the role claim
     * @param expiration  The token expiration or null to use default session length
     * @return {@link JWTClaimsSet.Builder} with common parts set
     */
    private JWTClaimsSet.Builder prebuildClaims(String subject, URI userId,
                                                Collection<? extends GrantedAuthority> authorities, Date expiration) {
        final Instant issued = issueTimestamp();
        final Date exp = expiration == null ? Date.from(issued.plusMillis(SecurityConstants.SESSION_TIMEOUT))
                                            : expiration;
        return new JWTClaimsSet.Builder()
                .subject(subject)
                .issueTime(Date.from(issued))
                .expirationTime(exp)
                .claim(SecurityConstants.JWT_ROLE_CLAIM, mapAuthoritiesToClaim(authorities))
                .jwtID(userId.toString());
    }


    private String sign(JWTClaimsSet claims, JOSEObjectType type) {
        return sign(claims, type, signer);
    }

    /**
     * Signs the specified claims, producing a compact serialized JWT.
     *
     * @param claims Claims to sign
     * @param type   Optional JOSE {@code typ} header value, or {@code null} for none
     * @param signer The signer to use
     * @return Compact serialized JWT
     */
    public static String sign(JWTClaimsSet claims, JOSEObjectType type, JWSSigner signer) {
        final JWSHeader.Builder headerBuilder = new JWSHeader.Builder(SIGNATURE_ALGORITHM);
        if (type != null) {
            headerBuilder.type(type);
        }
        final SignedJWT signedJWT = new SignedJWT(headerBuilder.build(), claims);
        try {
            signedJWT.sign(signer);
        } catch (JOSEException e) {
            throw new TermItException("Unable to sign JWT.", e);
        }
        return signedJWT.serialize();
    }

    /**
     * Generates Access Token JWT.
     *
     * @param newToken The token to generate
     * @return The token value
     */
    public String generatePAT(PersonalAccessToken newToken) {
        Date expiration = new Date(Long.MAX_VALUE);
        if (newToken.getExpirationDate() != null) {
            expiration = Date.from(newToken.getExpirationDate().atStartOfDay(ZoneId.systemDefault()).toInstant());
        }
        final JWTClaimsSet claims = prebuildClaims(newToken.getUri().toString(), newToken.getOwner().getUri(),
                                                   List.of(), expiration).build();
        return sign(claims, new JOSEObjectType(Constants.MediaType.JWT_ACCESS_TOKEN));
    }
}
