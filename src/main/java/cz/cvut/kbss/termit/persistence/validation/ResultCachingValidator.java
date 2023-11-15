/*
 * TermIt
 * Copyright (C) 2023 Czech Technical University in Prague
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
package cz.cvut.kbss.termit.persistence.validation;

import cz.cvut.kbss.termit.event.VocabularyContentModified;
import cz.cvut.kbss.termit.model.validation.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Lookup;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component("cachingValidator")
@Primary
@Profile("!no-cache")
public class ResultCachingValidator implements VocabularyContentValidator {

    private static final Logger LOG = LoggerFactory.getLogger(ResultCachingValidator.class);

    private final Map<Collection<URI>, List<ValidationResult>> validationCache = new ConcurrentHashMap<>();

    @Override
    public List<ValidationResult> validate(Collection<URI> vocabularyIris) {
        final Set<URI> copy = new HashSet<>(vocabularyIris);    // Defensive copy
        return new ArrayList<>(validationCache.computeIfAbsent(copy, uris -> getValidator().validate(vocabularyIris)));
    }

    @Lookup
    Validator getValidator() {
        return null;    // Will be replaced by Spring
    }

    @EventListener
    public void evictCache(VocabularyContentModified event) {
        LOG.debug("Vocabulary content modified, evicting validation result cache.");
        validationCache.clear();
    }
}
