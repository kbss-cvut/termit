package cz.cvut.kbss.termit.persistence.relationship;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.util.Constants;
import jakarta.annotation.Nonnull;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Resolves vocabulary relationships based on SKOS concept scheme mapping properties.
 *
 * @see Constants#SKOS_CONCEPT_MATCH_RELATIONSHIPS
 */
@Component
class SkosVocabularyRelationshipResolver implements VocabularyRelationshipResolver {

    private final EntityManager em;

    public SkosVocabularyRelationshipResolver(EntityManager em) {
        this.em = em;
    }

    @Transactional(readOnly = true)
    @Override
    @Nonnull
    public Set<URI> getRelatedVocabularies(@Nonnull URI vocabulary) {
        Objects.requireNonNull(vocabulary);
        return new HashSet<>(em.createNativeQuery("""
                                                          SELECT DISTINCT ?v WHERE {
                                                              ?t a ?term ;
                                                                 ?inVocabulary ?vocabulary ;
                                                                 ?y ?z .
                                                              ?z a ?term ;
                                                                 ?inVocabulary ?v .
                                                              FILTER (?v != ?vocabulary)
                                                              FILTER (?y IN (?cascadingRelationships))
                                                          }""", URI.class)
                               .setParameter("term", URI.create(SKOS.CONCEPT))
                               .setParameter("inVocabulary",
                                             URI.create(
                                                     cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku))
                               .setParameter("vocabulary", vocabulary)
                               .setParameter("cascadingRelationships",
                                             Constants.SKOS_CONCEPT_MATCH_RELATIONSHIPS)
                               .getResultList());
    }
}
