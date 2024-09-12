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
import cz.cvut.kbss.termit.event.VocabularyContentModifiedEvent;
import cz.cvut.kbss.termit.event.VocabularyCreatedEvent;
import cz.cvut.kbss.termit.event.VocabularyEvent;
import cz.cvut.kbss.termit.exception.TermItException;
import cz.cvut.kbss.termit.model.validation.ValidationResult;
import cz.cvut.kbss.termit.util.throttle.Throttle;
import cz.cvut.kbss.termit.util.throttle.ThrottledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Lookup;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

@Component("cachingValidator")
@Primary
@Profile("!no-cache")
public class ResultCachingValidator implements VocabularyContentValidator {

    private static final Logger LOG = LoggerFactory.getLogger(ResultCachingValidator.class);

    /**
     * Map of origin vocabulary IRI to vocabulary iri closure of imported vocabularies.
     * When the record is missing, the cache is considered as dirty.
     */
    private final Map<URI, Collection<URI>> vocabularyClosure = new ConcurrentHashMap<>();

    private final Map<URI, Collection<ValidationResult>> validationCache = new HashMap<>();

    /**
     * @return true when the cache contents are dirty and should be refreshed; false otherwise.
     */
    public boolean isNotDirty(@NonNull URI originVocabularyIri) {
        return vocabularyClosure.containsKey(originVocabularyIri);
    }

    private Optional<Collection<ValidationResult>> getCached(@NonNull URI originVocabularyIri) {
        synchronized (validationCache) {
            return Optional.ofNullable(validationCache.get(originVocabularyIri));
        }
    }

    @Throttle(value = "{#originVocabularyIri}", name="vocabularyValidation")
    @Transactional
    @Override
    @NonNull
    public ThrottledFuture<Collection<ValidationResult>> validate(@NonNull URI originVocabularyIri, @NonNull Collection<URI> vocabularyIris) {
        final Set<URI> iris = Set.copyOf(vocabularyIris);

        if (iris.isEmpty()) {
            LOG.warn("Validation of empty IRI list was requested for {}", originVocabularyIri);
            return ThrottledFuture.done(List.of());
        }

        Optional<Collection<ValidationResult>> cached = getCached(originVocabularyIri);
        if (isNotDirty(originVocabularyIri) && cached.isPresent()) {
            return ThrottledFuture.done(cached.get());
        }

        return ThrottledFuture.of(() -> runValidation(originVocabularyIri, iris)).setCachedResult(cached.orElse(null));
    }

    @NonNull
    private Collection<ValidationResult> runValidation(@NonNull URI originVocabularyIri, @NonNull final Set<URI> iris) {
        Optional<Collection<ValidationResult>> cached = getCached(originVocabularyIri);
        if (isNotDirty(originVocabularyIri) && cached.isPresent()) {
            return cached.get();
        }

        final Collection<ValidationResult> results;
        try {
            // executes real validation
            // get is safe here as long as we are on throttled thread from #validate method
            results = getValidator().validate(originVocabularyIri, iris).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TermItException(e);
        } catch (ExecutionException e) {
            throw new TermItException(e.getCause());
        }

        synchronized (validationCache) {
            vocabularyClosure.put(originVocabularyIri, Collections.unmodifiableCollection(iris));
            validationCache.put(originVocabularyIri, Collections.unmodifiableCollection(results));
        }

        return results;
    }

    @Lookup
    Validator getValidator() {
        return null;    // Will be replaced by Spring
    }

    /**
     * Marks cache related to the vocabulary from the event as dirty
     */
    @EventListener({VocabularyContentModifiedEvent.class, VocabularyCreatedEvent.class})
    public void markCacheDirty(VocabularyEvent event) {
        LOG.debug("Vocabulary content modified, marking cache as dirty for {}.", event.getVocabularyIri());
        // marked as dirty for specified vocabulary
        vocabularyClosure.remove(event.getVocabularyIri());
        // now mark all vocabularies importing modified vocabulary as dirty too
        synchronized (validationCache) {
            vocabularyClosure.keySet().forEach(originVocabularyIri -> {
                final Collection<URI> closure = vocabularyClosure.get(originVocabularyIri);
                if (closure != null && closure.contains(event.getVocabularyIri())) {
                    vocabularyClosure.remove(originVocabularyIri);
                }
            });
            if (event instanceof VocabularyCreatedEvent) {
                validationCache.remove(event.getVocabularyIri());
            }
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
