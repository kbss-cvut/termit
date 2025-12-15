package cz.cvut.kbss.termit.security;

import io.jsonwebtoken.Claims;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Ensures that the token contains {@link UserDetails} in {@link Claims#SUBJECT sub} claim
 * and validates the details with {@link AccountStatusUserDetailsChecker}.
 */
public class JwtUserDetailsValidator implements OAuth2TokenValidator<Jwt> {
    private static final AccountStatusUserDetailsChecker checker = new AccountStatusUserDetailsChecker();

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        final Object subject = token.getClaim(Claims.SUBJECT);
        try {
            if (subject instanceof UserDetails userDetails) {
                checker.check(userDetails);
                return OAuth2TokenValidatorResult.success();
            }
        } catch (Exception e) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(OAuth2ErrorCodes.ACCESS_DENIED, e.getMessage(), null));
        }
        return OAuth2TokenValidatorResult.failure();
    }
}
