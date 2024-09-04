package cz.cvut.kbss.termit.security;

import cz.cvut.kbss.termit.security.model.TermItUserDetails;
import cz.cvut.kbss.termit.service.security.SecurityUtils;
import cz.cvut.kbss.termit.service.security.TermItUserDetailsService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtException;

import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @see #decode(String)
 */
public class TermitJwtDecoder implements org.springframework.security.oauth2.jwt.JwtDecoder {

    private final JwtUtils jwtUtils;

    private final TermItUserDetailsService userDetailsService;

    public TermitJwtDecoder(JwtUtils jwtUtils, TermItUserDetailsService userDetailsService) {
        this.jwtUtils = jwtUtils;
        this.userDetailsService = userDetailsService;
    }

    /**
     * Decodes JWT token (without the {@code Bearer} prefix)
     * and ensures its validity.
     * @throws JwtException with cause, when token could not be decoded or verified
     */
    @Override
    public Jwt decode(String token) throws JwtException {
        try {
            final Jws<Claims> expanded = jwtUtils.getClaimsFromToken(token);
            Objects.requireNonNull(expanded);
            Objects.requireNonNull(expanded.getBody());
            Objects.requireNonNull(expanded.getHeader());
            final Claims claims = expanded.getBody();
            Objects.requireNonNull(claims.getIssuedAt());
            Objects.requireNonNull(claims.getExpiration());
            final TermItUserDetails tokenDetails = jwtUtils.extractUserInfo(claims);
            final TermItUserDetails existingDetails = userDetailsService.loadUserByUsername(tokenDetails.getUsername());

            SecurityUtils.verifyAccountStatus(existingDetails.getUser());

            claims.put("scope", existingDetails.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                                               .collect(Collectors.toSet()));
            claims.putIfAbsent(JwtClaimNames.SUB, existingDetails);

            return new Jwt(token, claims.getIssuedAt().toInstant(), claims.getExpiration()
                                                                          .toInstant(), expanded.getHeader(), claims);
        } catch (cz.cvut.kbss.termit.exception.JwtException | NullPointerException e) {
            throw new JwtException(e.getMessage(), e);
        }
    }
}
