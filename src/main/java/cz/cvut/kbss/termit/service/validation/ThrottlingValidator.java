/*
 * TermIt
 * Copyright (C) 2025 Czech Technical University in Prague
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.service.validation;

import cz.cvut.kbss.termit.event.VocabularyValidationFinishedEvent;
import cz.cvut.kbss.termit.model.validation.ValidationResult;
import cz.cvut.kbss.termit.persistence.context.VocabularyContextMapper;
import cz.cvut.kbss.termit.service.business.VocabularyService;
import cz.cvut.kbss.termit.util.throttle.Throttle;
import cz.cvut.kbss.termit.util.throttle.ThrottledFuture;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Collection;
import java.util.List;

@Component
public class ThrottlingValidator implements VocabularyContentValidator {

    private static final Logger LOG = LoggerFactory.getLogger(ThrottlingValidator.class);

    private final RepositoryContextValidator validator;
    private final VocabularyService vocabularyService;
    private final VocabularyContextMapper vocabularyContextMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public ThrottlingValidator(RepositoryContextValidator validator, VocabularyService vocabularyService,
                               VocabularyContextMapper vocabularyContextMapper,
                               ApplicationEventPublisher eventPublisher) {
        this.validator = validator;
        this.vocabularyService = vocabularyService;
        this.vocabularyContextMapper = vocabularyContextMapper;
        this.eventPublisher = eventPublisher;
    }

    @Throttle(value = "{#originVocabularyIri}", name = "vocabularyValidation")
    @Override
    @Nonnull
    public ThrottledFuture<Collection<ValidationResult>> validate(final @Nonnull URI originVocabularyIri,
                                                                  final @Nonnull Collection<URI> vocabularyIris) {
        if (vocabularyIris.isEmpty()) {
            return ThrottledFuture.done(List.of());
        }

        return ThrottledFuture.of(() -> {
            final String originVocabularyLanguage = vocabularyService.getPrimaryLanguage(originVocabularyIri);
            final List<ValidationResult> results = runValidation(vocabularyIris, originVocabularyLanguage);
            eventPublisher.publishEvent(
                    new VocabularyValidationFinishedEvent(this, originVocabularyIri, vocabularyIris, results));
            return results;
        });
    }

    protected synchronized List<ValidationResult> runValidation(@Nonnull Collection<URI> vocabularyIris, String language) {
        LOG.debug("Validating vocabularies {}", vocabularyIris);
        return validator.validate(vocabularyIris.stream().map(
                vocabularyContextMapper::getVocabularyContext).toList(), language);
    }
}
