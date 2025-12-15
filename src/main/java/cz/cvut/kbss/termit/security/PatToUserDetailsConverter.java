package cz.cvut.kbss.termit.security;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.userdetails.UserDetails;

public class PatToUserDetailsConverter implements Converter<Object, UserDetails> {

    public PatToUserDetailsConverter() {

    }

    @Nullable
    @Override
    public UserDetails convert(@Nonnull Object source) {
        throw new UnsupportedOperationException("Personal access tokens are not yet supported.");
    }
}
