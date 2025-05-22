/*
 * TermIt
 * Copyright (C) 2025 Czech Technical University in Prague
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
