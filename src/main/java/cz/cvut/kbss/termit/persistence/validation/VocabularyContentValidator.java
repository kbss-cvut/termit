package cz.cvut.kbss.termit.persistence.validation;

import cz.cvut.kbss.termit.model.validation.ValidationResult;

import java.net.URI;
import java.util.Collection;
import java.util.List;

/**
 * Allows validating the content of vocabularies based on preconfigured rules.
 */
public interface VocabularyContentValidator {

    /**
     * Validates the content of vocabularies with the specified identifiers.
     * <p>
     * The vocabularies are validated together, as a single unit.
     *
     * @param vocabularyIris Vocabulary identifiers
     * @return List of violations of validation rules. Empty list if there are not violations
     */
    List<ValidationResult> validate(final Collection<URI> vocabularyIris);
}
