package cz.cvut.kbss.termit.security;

import cz.cvut.kbss.termit.security.model.AuthenticationToken;
import cz.cvut.kbss.termit.security.model.TermItUserDetails;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Converts the JWT into {@link AuthenticationToken} using the user details from the subject claim.
 * @implNote Expects the {@link JwtClaimNames#SUB subject} claim in the JWT to contain {@link TermItUserDetails}.
 */
public class PatAuthenticationConverter implements Converter<Jwt, AuthenticationToken> {
    @Override
    public AuthenticationToken convert(Jwt source) {
        if (source.getClaim(JwtClaimNames.SUB) instanceof TermItUserDetails userDetails) {
            return new AuthenticationToken(userDetails.getAuthorities(), userDetails);
        }
        throw new JwtException("Unable to retrieve mapped user details from JWT.");
    }
}
