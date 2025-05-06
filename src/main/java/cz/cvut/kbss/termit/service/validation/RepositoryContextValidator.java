package cz.cvut.kbss.termit.service.validation;

import cz.cvut.kbss.termit.model.validation.ValidationResult;
import jakarta.annotation.Nonnull;

import java.net.URI;
import java.util.List;

/**
 * Validates the content of repository contexts.
 */
public interface RepositoryContextValidator {

    /**
     * Validates the content of the specified repository contexts.
     *
     * @param contexts Repository contexts to validate
     * @param language Language for checking presence of string values
     * @return List of validation violations
     */
    @Nonnull
    List<ValidationResult> validate(@Nonnull List<URI> contexts, @Nonnull String language);
}
