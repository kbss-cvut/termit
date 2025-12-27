package cz.cvut.kbss.termit.security;

import cz.cvut.kbss.termit.model.PersonalAccessToken;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.security.model.TermItUserDetails;
import cz.cvut.kbss.termit.service.business.PersonalAccessTokenService;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;

import java.net.URI;
import java.util.Objects;

/**
 * Converts personal access token identifier ({@code source}) into {@link TermItUserDetails}.
 */
public class PatToUserDetailsConverter implements Converter<Object, TermItUserDetails> {
    private static final Logger LOG = LoggerFactory.getLogger(PatToUserDetailsConverter.class);
    private final PersonalAccessTokenService accessTokenService;

    public PatToUserDetailsConverter(PersonalAccessTokenService accessTokenService) {
        this.accessTokenService = accessTokenService;
    }

    @Nullable
    @Override
    public TermItUserDetails convert(@Nonnull Object source) {
        try {
            URI patIdentifier = null;
            if (source instanceof URI uriSource) {
                patIdentifier = uriSource;
            } else if (source instanceof String stringSource) {
                patIdentifier = URI.create(stringSource);
            }
            final PersonalAccessToken token = accessTokenService.findValid(patIdentifier);
            final UserAccount userAccount = token.getOwner();
            Objects.requireNonNull(userAccount, "No user account found for the specified PAT");
            accessTokenService.updateLastUsed(token);
            return new TermItUserDetails(userAccount);
        } catch (Exception e) {
            LOG.trace("Failed to resolve PAT {} to UserDetails with error: {}", source, e.getMessage());
        }
        return null;
    }
}
