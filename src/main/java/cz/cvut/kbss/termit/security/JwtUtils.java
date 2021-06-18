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

import cz.cvut.kbss.termit.exception.IncompleteJwtException;
import cz.cvut.kbss.termit.exception.JwtException;
import cz.cvut.kbss.termit.exception.TokenExpiredException;
import cz.cvut.kbss.termit.model.AbstractUser;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.security.model.TermItUserDetails;
import cz.cvut.kbss.termit.util.ConfigParam;
import cz.cvut.kbss.termit.util.Configuration;
import io.jsonwebtoken.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class JwtUtils {

    private final Configuration config;

    @Autowired
    public JwtUtils(Configuration config) {
        this.config = config;
    }

    /**
     * Generates a JSON Web Token for the specified authenticated user.
     *
     * @param user User info
     * @return Generated JWT hash
     */
    public String generateToken(AbstractUser user, Collection<? extends GrantedAuthority> authorities) {
        final Date issued = new Date();
        return Jwts.builder().setSubject(user.getUsername())
                   .setId(user.getUri().toString())
                   .setIssuedAt(issued)
                   .setExpiration(new Date(issued.getTime() + SecurityConstants.SESSION_TIMEOUT))
                   .claim(SecurityConstants.JWT_ROLE_CLAIM, mapAuthoritiesToClaim(authorities))
                   .signWith(SignatureAlgorithm.HS512, getJwtSecretKey())
                   .compact();
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
            return Jwts.parser().setSigningKey(getJwtSecretKey())
                       .parseClaimsJws(token).getBody();
        } catch (MalformedJwtException | UnsupportedJwtException e) {
            throw new JwtException("Unable to parse the specified JWT.", e);
        } catch (SignatureException e) {
            throw new JwtException("Invalid signature of the specified JWT.", e);
        } catch (ExpiredJwtException e) {
            throw new TokenExpiredException(e.getMessage());
        }
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
        final Date issuedAt = new Date();
        claims.setIssuedAt(issuedAt);
        claims.setExpiration(new Date(issuedAt.getTime() + SecurityConstants.SESSION_TIMEOUT));
        return Jwts.builder().setClaims(claims)
                   .signWith(SignatureAlgorithm.HS512, getJwtSecretKey()).compact();
    }

    private String getJwtSecretKey() {
        return config.getJwt().getSecretKey();
    }
}
