package cz.cvut.kbss.termit.persistence.validation;

import cz.cvut.kbss.termit.exception.UnsupportedOperationException;
import cz.cvut.kbss.termit.model.validation.ValidationResult;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;

/**
 * Does nothing when asked to validate.
 */
public class NoopRepositoryContextValidator implements RepositoryContextValidator {

    private static final Logger LOG = LoggerFactory.getLogger(NoopRepositoryContextValidator.class);

    @NotNull
    @Override
    public List<ValidationResult> validate(@NotNull List<URI> contexts, @NotNull String language) {
        LOG.trace("No-op when attempting to validate {}.", contexts);
        throw new UnsupportedOperationException("Validation service not configured.");
    }
}
