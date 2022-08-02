package cz.cvut.kbss.termit.persistence.context;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.event.EvictCacheEvent;
import cz.cvut.kbss.termit.event.VocabularyCreatedEvent;
import cz.cvut.kbss.termit.exception.AmbiguousVocabularyContextException;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

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
 */
public class CachingVocabularyContextMapper implements VocabularyContextMapper {

    private static final Logger LOG = LoggerFactory.getLogger(CachingVocabularyContextMapper.class);

    private final EntityManager em;

    private Map<URI, List<URI>> contexts;

    public CachingVocabularyContextMapper(EntityManager em) {
        this.em = em;
    }

    /**
     * Loads vocabulary context info into memory (cache).
     */
    @EventListener(value = {VocabularyCreatedEvent.class, EvictCacheEvent.class, ContextRefreshedEvent.class})
    public void load() {
        this.contexts = new HashMap<>();
        em.createNativeQuery("SELECT ?v ?g WHERE { GRAPH ?g { ?v a ?type . } } ORDER BY ?v")
          .setParameter("type", URI.create(Vocabulary.s_c_slovnik))
          .getResultStream().forEach(row -> {
              assert row instanceof Object[];
              assert ((Object[]) row).length == 2;
              final Object[] bindingSet = (Object[]) row;
              final List<URI> ctx = contexts.computeIfAbsent((URI) bindingSet[0], (k) -> new ArrayList<>());
              ctx.add((URI) bindingSet[1]);
          });
    }

    /**
     * Gets identifier of the repository context in which vocabulary with the specified identifier is stored.
     *
     * @param vocabularyUri Vocabulary identifier
     * @return Repository context identifier
     */
    public URI getVocabularyContext(URI vocabularyUri) {
        if (!contexts.containsKey(vocabularyUri)) {
            LOG.debug("No context mapped for vocabulary {}, returning the vocabulary IRI as context identifier.",
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
}
