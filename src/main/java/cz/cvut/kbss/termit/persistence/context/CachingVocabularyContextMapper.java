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
package cz.cvut.kbss.termit.persistence.context;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.event.EvictCacheEvent;
import cz.cvut.kbss.termit.event.VocabularyCreatedEvent;
import cz.cvut.kbss.termit.exception.AmbiguousVocabularyContextException;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cz.cvut.kbss.termit.util.Utils.uriToString;

/**
 * Caching implementation of the {@link VocabularyContextMapper}.
 * <p>
 * Context map is loaded on startup and reloaded every time a vocabulary is created.
 * <p>
 * Note that only <i>canonical</i> versions of vocabularies are considered for context resolution.
 */
@Component
@Profile("!no-cache")
public class CachingVocabularyContextMapper extends DefaultVocabularyContextMapper
        implements SmartInitializingSingleton, Ordered {

    private static final Logger LOG = LoggerFactory.getLogger(CachingVocabularyContextMapper.class);

    private Map<URI, List<URI>> contexts;

    public CachingVocabularyContextMapper(EntityManager em) {
        super(em);
    }

    /**
     * Loads vocabulary context info into memory (cache).
     */
    @EventListener(value = {VocabularyCreatedEvent.class, EvictCacheEvent.class})
    public void load() {
        this.contexts = new HashMap<>();
        em.createNativeQuery("SELECT ?v ?g WHERE { " +
                                     "GRAPH ?g { " +
                                     "?v a ?type . " +
                                     "FILTER NOT EXISTS { ?g ?basedOnVersion ?canonical . } " +
                                     "}}")
          .setParameter("type", URI.create(Vocabulary.s_c_slovnik))
          .setParameter("basedOnVersion", URI.create(Vocabulary.s_p_d_sgov_pracovni_prostor_pojem_vychazi_z_verze))
          .getResultStream().forEach(row -> {
              assert row instanceof Object[];
              assert ((Object[]) row).length == 2;
              final Object[] bindingSet = (Object[]) row;
              final List<URI> ctx = contexts.computeIfAbsent((URI) bindingSet[0], k -> new ArrayList<>());
              ctx.add((URI) bindingSet[1]);
          });
    }

    @Override
    public void afterSingletonsInstantiated() {
        load();
    }

    /**
     * Gets identifier of the repository context in which vocabulary with the specified identifier is stored.
     *
     * @param vocabularyUri Vocabulary identifier
     * @return Repository context identifier
     */
    @Override
    public URI getVocabularyContext(URI vocabularyUri) {
        if (!contexts.containsKey(vocabularyUri)) {
            LOG.trace("No context mapped for vocabulary {}, returning the vocabulary IRI as context identifier.",
                      uriToString(vocabularyUri));
            return vocabularyUri;
        }
        final List<URI> vocabularyContexts = contexts.get(vocabularyUri);
        if (vocabularyContexts.size() > 1) {
            throw new AmbiguousVocabularyContextException(
                    "Multiple repository contexts found for vocabulary " + uriToString(vocabularyUri));
        }
        return vocabularyContexts.get(0);
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
