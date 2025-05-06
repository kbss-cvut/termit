package cz.cvut.kbss.termit.persistence.validation;

import cz.cvut.kbss.termit.model.validation.ValidationResult;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.List;

/**
 * Does nothing when asked to validate.
 */
public class NoopRepositoryContextValidator implements RepositoryContextValidator {
    @NotNull
    @Override
    public List<ValidationResult> validate(@NotNull List<URI> contexts, @NotNull String language) {
        return List.of();
    }
}
