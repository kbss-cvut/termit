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
import cz.cvut.kbss.termit.persistence.dao.VocabularyDao;
import cz.cvut.kbss.termit.util.throttle.ThrottledFuture;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Lookup;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
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
     * Map of vocabulary IRI to boolean.
     * True when cache for the vocabulary is dirty.
     */
    private final Map<URI, Boolean> cacheDirtiness = new ConcurrentHashMap<>();

    private final Map<URI, List<ValidationResult>> validationCache = new HashMap<>();

    private final VocabularyDao vocabularyDao;

    @Autowired
    public ResultCachingValidator(VocabularyDao vocabularyDao) {
        this.vocabularyDao = vocabularyDao;
    }

    /**
     * @return true when the cache contents are dirty and should be refreshed; false otherwise.
     */
    public boolean isDirty(URI vocabularyIris) {
        return cacheDirtiness.getOrDefault(vocabularyIris, true);
    }

    private List<ValidationResult> getCached(Set<URI> vocabularyIris) {
        synchronized (validationCache) {
            return vocabularyIris.stream().flatMap(v -> validationCache.getOrDefault(v, List.of()).stream()).toList();
        }
    }

    @Override
    public @NotNull ThrottledFuture<List<ValidationResult>> validate(@NotNull Collection<URI> vocabularyIris) {
        final Set<URI> iris = new HashSet<>(vocabularyIris);

        if (iris.isEmpty()) {
            return ThrottledFuture.done(List.of());
        }

        boolean cacheDirty = iris.stream().anyMatch(this::isDirty);
        List<ValidationResult> cached = getCached(iris);
        if (!cacheDirty) {
            return ThrottledFuture.done(cached);
        }

        return ThrottledFuture.of(() -> runValidation(iris)).setCachedResult(cached.isEmpty() ? null : cached);
    }


    private @NotNull List<ValidationResult> runValidation(@NotNull final Set<URI> iris) {
        final List<ValidationResult> results = getValidator().runValidation(iris);

        final Map<URI, URI> termToVocabularyMap = vocabularyDao.getTermToVocabularyMap(iris);

        boolean cacheDirty = iris.stream().anyMatch(this::isDirty);
        if (!cacheDirty) {
            return getCached(iris);
        }

        synchronized (validationCache) {
            iris.forEach(vocabulary -> {
                cacheDirtiness.put(vocabulary, false);
                validationCache.computeIfAbsent(vocabulary, k -> new ArrayList<>()).clear();
            });
            results.parallelStream().forEach(result -> {
                final URI vocabulary = termToVocabularyMap.get(result.getTermUri());
                validationCache.get(vocabulary).add(result);
            });
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
        cacheDirtiness.put(event.getVocabularyIri(), true);
    }

    @EventListener(EvictCacheEvent.class)
    public void evictCache() {
        LOG.debug("Validation cache cleared");
        cacheDirtiness.clear();
        validationCache.clear();
    }
}
