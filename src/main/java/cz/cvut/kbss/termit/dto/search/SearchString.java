package cz.cvut.kbss.termit.dto.search;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Search string and language.
 *
 * @param searchString String to search by, possibly empty, but should not be {@code null}
 * @param language     Language for the search string, can be {@code null}
 */
public record SearchString(@Nonnull String searchString, @Nullable String language) {
}
