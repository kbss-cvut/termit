package cz.cvut.kbss.termit.security;

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
            if (source instanceof URI) {
                patIdentifier = (URI) source;
            } else if (source instanceof String uri) {
                patIdentifier = URI.create(uri);
            }
            UserAccount userAccount = accessTokenService.findUserAccountByTokenId(patIdentifier);
            Objects.requireNonNull(userAccount, "No user account found for the specified PAT");
            return new TermItUserDetails(userAccount);
        } catch (Exception e) {
            LOG.trace("Failed to resolve PAT {} to UserDetails with error: {}", source, e.getMessage());
        }
        return null;
    }
}
