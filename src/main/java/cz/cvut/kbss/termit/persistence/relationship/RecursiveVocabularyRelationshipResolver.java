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
import jakarta.annotation.Nonnull;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;

/**
 * Resolves related vocabularies recursively using all the other vocabulary relationship resolvers.
 * <p>
 * Classification vocabularies are excluded from the recursion, as they may be connected to many other vocabularies,
 * leading to false relationships.
 */
@Component
@Primary
public class RecursiveVocabularyRelationshipResolver implements VocabularyRelationshipResolver {

    private final List<VocabularyRelationshipResolver> resolvers;

    private final EntityManager em;

    public RecursiveVocabularyRelationshipResolver(List<VocabularyRelationshipResolver> resolvers, EntityManager em) {
        this.resolvers = resolvers;
        this.em = em;
    }

    @Transactional(readOnly = true)
    @Nonnull
    @Override
    public Set<URI> getRelatedVocabularies(@Nonnull URI vocabulary) {
        Objects.requireNonNull(vocabulary);
        final Set<URI> result = new HashSet<>();
        final Stack<URI> toProcess = new Stack<>();
        toProcess.add(vocabulary);
        final List<URI> toSkipRecursion = getClassificationVocabularies(vocabulary);
        while (!toProcess.isEmpty()) {
            final URI item = toProcess.pop();
            final Set<URI> toAdd = resolvers.stream()
                                            .flatMap(r -> r.getRelatedVocabularies(item).stream())
                                            .collect(Collectors.toSet());
            toAdd.removeAll(result);
            result.addAll(toAdd);
            // Do not recurse into classification vocabularies, as they may be connected to many other vocabularies we
            // are not interested in
            toSkipRecursion.forEach(toAdd::remove);
            toProcess.addAll(toAdd);
        }
        return result;
    }

    private List<URI> getClassificationVocabularies(URI vocabulary) {
        return em.createNativeQuery("""
                                            SELECT DISTINCT ?typeVocabulary WHERE {
                                                     ?x a ?termType ;
                                                     ?inVocabulary ?vocabulary ;
                                                     a ?type .
                                                     ?type a ?termType ;
                                                     ?inVocabulary ?typeVocabulary .
                                                 }
                                            """, URI.class)
                 .setParameter("vocabulary", vocabulary)
                 .setParameter("termType", URI.create(SKOS.CONCEPT))
                 .setParameter("inVocabulary",
                               URI.create(cz.cvut.kbss.termit.util.Vocabulary.s_p_je_pojmem_ze_slovniku))
                 .getResultList();
    }
}
