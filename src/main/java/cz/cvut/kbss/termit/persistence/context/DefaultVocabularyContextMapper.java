package cz.cvut.kbss.termit.persistence.context;

import cz.cvut.kbss.jopa.exceptions.NoResultException;
import cz.cvut.kbss.jopa.exceptions.NoUniqueResultException;
import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.exception.AmbiguousVocabularyContextException;
import cz.cvut.kbss.termit.util.Vocabulary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Objects;

import static cz.cvut.kbss.termit.util.Utils.uriToString;

/**
 * Default implementation of the context mapper resolves vocabulary context on every call.
 * <p>
 * This incurs a performance penalty of executing a simple query, but does not suffer from potentially stale cache
 * data.
 */
@Component
@Profile("no-cache")
public class DefaultVocabularyContextMapper implements VocabularyContextMapper {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultVocabularyContextMapper.class);

    private final EntityManager em;

    public DefaultVocabularyContextMapper(EntityManager em) {
        this.em = em;
    }

    @Override
    public URI getVocabularyContext(URI vocabularyUri) {
        Objects.requireNonNull(vocabularyUri);
        try {
            return em.createNativeQuery("SELECT ?g WHERE { GRAPH ?g { ?vocabulary a ?type . } }", URI.class)
                     .setParameter("type", URI.create(Vocabulary.s_c_slovnik))
                     .setParameter("vocabulary", vocabularyUri)
                     .getSingleResult();
        } catch (NoResultException e) {
            LOG.debug("No context mapped for vocabulary {}, returning the vocabulary IRI as context identifier.",
                      uriToString(vocabularyUri));
            return vocabularyUri;
        } catch (NoUniqueResultException e) {
            throw new AmbiguousVocabularyContextException(
                    "Multiple repository contexts found for vocabulary " + uriToString(vocabularyUri));
        }
    }
}
