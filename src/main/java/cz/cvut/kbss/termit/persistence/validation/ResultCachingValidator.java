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

import cz.cvut.kbss.termit.event.EvictCacheEvent;
import cz.cvut.kbss.termit.event.VocabularyContentModified;
import cz.cvut.kbss.termit.model.validation.ValidationResult;
import cz.cvut.kbss.termit.util.throttle.ThrottledFuture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Lookup;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component("cachingValidator")
@Primary
@Profile("!no-cache")
public class ResultCachingValidator implements VocabularyContentValidator {

    private static final Logger LOG = LoggerFactory.getLogger(ResultCachingValidator.class);

    /**
     * Map of origin vocabulary IRI to vocabulary iri closure of imported vocabularies.
     * When the value is null, then the cache entry is considered dirty.
     */
    private final Map<URI, @NotNull Collection<URI>> vocabularyClosure = new ConcurrentHashMap<>();

    private final Map<URI, @NotNull List<ValidationResult>> validationCache = new HashMap<>();

    /**
     * @return true when the cache contents are dirty and should be refreshed; false otherwise.
     */
    public boolean isNotDirty(@NotNull URI originVocabularyIri) {
        return vocabularyClosure.containsKey(originVocabularyIri);
    }

    private List<ValidationResult> getCached(@NotNull URI originVocabularyIri) {
        synchronized (validationCache) {
            return validationCache.getOrDefault(originVocabularyIri, List.of());
        }
    }

    @Override
    public @NotNull ThrottledFuture<Collection<ValidationResult>> validate(@NotNull URI originVocabularyIri, @NotNull Collection<URI> vocabularyIris) {
        final Set<URI> iris = Set.copyOf(vocabularyIris);

        if (iris.isEmpty()) {
            return ThrottledFuture.done(List.of());
        }

        List<ValidationResult> cached = getCached(originVocabularyIri);
        if (isNotDirty(originVocabularyIri)) {
            return ThrottledFuture.done(cached);
        }

        return ThrottledFuture.of(() -> runValidation(originVocabularyIri, iris)).setCachedResult(cached.isEmpty() ? null : cached);
    }


    private @NotNull Collection<ValidationResult> runValidation(@NotNull URI originVocabularyIri, @NotNull final Set<URI> iris) {
        if (isNotDirty(originVocabularyIri)) {
            return getCached(originVocabularyIri);
        }

        final List<ValidationResult> results = getValidator().runValidation(iris);

        synchronized (validationCache) {
            vocabularyClosure.put(originVocabularyIri, Collections.unmodifiableCollection(iris));
            validationCache.put(originVocabularyIri, Collections.unmodifiableList(results));
        }

        return results;
    }

    @Lookup
    Validator getValidator() {
        return null;    // Will be replaced by Spring
    }

    @EventListener
    public void evictVocabularyCache(VocabularyContentModified event) {
        LOG.debug("Vocabulary content modified, marking cache as dirty for {}.", event.getVocabularyIri());
        // marked as dirty for specified vocabulary
        vocabularyClosure.remove(event.getVocabularyIri());
        // now mark all vocabularies importing modified vocabulary as dirty too
        synchronized (validationCache) {
            vocabularyClosure.keySet().forEach(originVocabularyIri -> {
                final @Nullable Collection<URI> closure = vocabularyClosure.get(originVocabularyIri);
                if (closure != null && closure.contains(event.getVocabularyIri())) {
                    vocabularyClosure.remove(originVocabularyIri);
                }
            });
        }
    }

    @EventListener(EvictCacheEvent.class)
    public void evictCache() {
        LOG.debug("Validation cache cleared");
        synchronized (validationCache) {
            vocabularyClosure.clear();
            validationCache.clear();
        }
    }
}
