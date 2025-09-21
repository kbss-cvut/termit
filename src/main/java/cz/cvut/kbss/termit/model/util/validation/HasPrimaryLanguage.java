package cz.cvut.kbss.termit.model.util.validation;


import jakarta.annotation.Nullable;

/**
 * Specifies that the entity has a primary language.
 */
public interface HasPrimaryLanguage {
    @Nullable String getPrimaryLanguage();
}
