package cz.cvut.kbss.termit.persistence.relationship;

import cz.cvut.kbss.jopa.model.EntityManager;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;

/**
 * Resolves related vocabularies based on explicit imports.
 */
@Component
class ImportBasedVocabularyRelationshipResolver implements VocabularyRelationshipResolver {

    private final EntityManager em;

    public ImportBasedVocabularyRelationshipResolver(EntityManager em) {
        this.em = em;
    }

    @Transactional(readOnly = true)
    @Nonnull
    @Override
    public Set<URI> getRelatedVocabularies(@Nonnull URI vocabulary) {
        return new HashSet<>(
                em.createNativeQuery("SELECT DISTINCT ?imported WHERE { ?v ?imports ?imported . }", URI.class)
                  .setParameter("imports",
                                URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_importuje_slovnik))
                  .setParameter("v", vocabulary).getResultList());
    }
}
