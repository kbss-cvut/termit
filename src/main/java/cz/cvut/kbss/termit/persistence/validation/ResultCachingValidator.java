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
