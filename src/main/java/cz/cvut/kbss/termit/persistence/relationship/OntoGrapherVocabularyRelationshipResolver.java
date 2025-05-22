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
import cz.cvut.kbss.termit.util.Vocabulary;
import jakarta.annotation.Nonnull;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Resolves vocabulary relationships based on relationships between terms created in <a
 * href="https://github.com/datagov-cz/ontoGrapher">OntoGrapher</a>.
 */
@Profile("ontographer")
@Component
class OntoGrapherVocabularyRelationshipResolver implements VocabularyRelationshipResolver {

    private final EntityManager em;

    public OntoGrapherVocabularyRelationshipResolver(EntityManager em) {
        this.em = em;
    }

    @Transactional(readOnly = true)
    @Nonnull
    @Override
    public Set<URI> getRelatedVocabularies(@Nonnull URI vocabulary) {
        Objects.requireNonNull(vocabulary);
        final List vocabularyList = em.createNativeQuery("""
                                                                 PREFIX og: <http://onto.fel.cvut.cz/ontologies/application/ontoGrapher/>

                                                                 SELECT DISTINCT ?linkVocabulary ?sourceVocabulary ?targetVocabulary where {
                                                                    ?link a og:link ;
                                                                        og:active "true" ;
                                                                        og:iri ?linkIri ;
                                                                        og:source ?source ;
                                                                        og:target ?target .
                                                                    OPTIONAL {
                                                                        ?linkIri a ?term ;
                                                                            ?inVocabulary ?linkVocabulary .
                                                                    }
                                                                    ?source a ?term ;
                                                                        ?inVocabulary ?sourceVocabulary .
                                                                    ?target a ?term ;
                                                                        ?inVocabulary ?targetVocabulary .
                                                                    FILTER (?linkVocabulary = ?vocabulary || ?sourceVocabulary = ?vocabulary || ?targetVocabulary = ?vocabulary)
                                                                 }
                                                                 """).setParameter("vocabulary", vocabulary)
                                      .setParameter("inVocabulary", URI.create(Vocabulary.s_p_je_pojmem_ze_slovniku))
                                      .setParameter("term", URI.create(SKOS.CONCEPT))
                                      .getResultList();
        final Set<URI> result = new HashSet<>(vocabularyList.size());
        vocabularyList.forEach(elem -> {
            final Object[] row = (Object[]) elem;
            result.addAll(Stream.of(row).filter(Objects::nonNull).map(URI.class::cast).collect(Collectors.toSet()));
        });
        result.remove(vocabulary);
        return result;
    }
}
