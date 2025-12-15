package cz.cvut.kbss.termit.security;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Converts given username (must be a {@link String})
 * to {@link UserDetails} fetched using the {@link UserDetailsService}.
 */
public class UsernameToUserDetailsConverter implements Converter<Object, UserDetails> {
    private static final Logger LOG = LoggerFactory.getLogger(UsernameToUserDetailsConverter.class);
    private final UserDetailsService userDetailsService;

    public UsernameToUserDetailsConverter(UserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Nullable
    @Override
    public UserDetails convert(@Nonnull Object source) {
        try {
            if (source instanceof String username) {
                return userDetailsService.loadUserByUsername(username);
            }
        } catch (UsernameNotFoundException e) {
            LOG.trace("Failed to resolve username {} to UserDetails", source);
        }
        return null;
    }
}
