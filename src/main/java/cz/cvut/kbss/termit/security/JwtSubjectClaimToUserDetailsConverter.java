package cz.cvut.kbss.termit.security;

import cz.cvut.kbss.termit.util.Constants;
import io.jsonwebtoken.Claims;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.jwt.JoseHeaderNames;

import java.util.HashMap;
import java.util.Map;

/**
 * Converts {@link Claims#SUBJECT} claim to {@link UserDetails}
 * based on the value of {@link JoseHeaderNames#TYP typ} claim.
 */
public class JwtSubjectClaimToUserDetailsConverter implements Converter<Map<String, Object>, Map<String, Object>> {
    private final UsernameToUserDetailsConverter usernameToUserDetailsConverter;
    private final PatToUserDetailsConverter patToUserDetailsConverter;

    public JwtSubjectClaimToUserDetailsConverter(UsernameToUserDetailsConverter usernameToUserDetailsConverter,
                                                 PatToUserDetailsConverter patToUserDetailsConverter) {
        this.usernameToUserDetailsConverter = usernameToUserDetailsConverter;
        this.patToUserDetailsConverter = patToUserDetailsConverter;
    }

    @Override
    public Map<String, Object> convert(Map<String, Object> source) {
        final Object claim = source.get(JoseHeaderNames.TYP);
        Converter<Object, ? extends UserDetails> converter = usernameToUserDetailsConverter;
        if (Constants.MediaType.JWT_ACCESS_TOKEN.equals(claim)) {
            converter = patToUserDetailsConverter;
        }

        return convertWith(converter, source);
    }

    private Map<String, Object> convertWith(Converter<Object, ? extends UserDetails> converter, Map<String, Object> source) {
        final Object claimValue = source.get(Claims.SUBJECT);
        if (claimValue != null) {
            final Map<String, Object> mappedClaims = new HashMap<>(source);
            mappedClaims.put(Claims.SUBJECT, converter.convert(claimValue));
            return mappedClaims;
        }
        return source;
    }
}
