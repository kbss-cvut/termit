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

import jakarta.annotation.Nonnull;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Resolves related vocabularies recursively using all the other vocabulary relationship resolvers.
 */
@Component
@Primary
public class RecursiveVocabularyRelationshipResolver implements VocabularyRelationshipResolver {

    private final List<VocabularyRelationshipResolver> resolvers;

    public RecursiveVocabularyRelationshipResolver(List<VocabularyRelationshipResolver> resolvers) {
        this.resolvers = resolvers;
    }

    @Transactional(readOnly = true)
    @Nonnull
    @Override
    public Set<URI> getRelatedVocabularies(@Nonnull URI vocabulary) {
        Objects.requireNonNull(vocabulary);
        final List<URI> result = new ArrayList<>();
        result.add(vocabulary);
        // Using old-school iteration to prevent concurrent modification issues when adding items to list under iteration
        for (int i = 0; i < result.size(); i++) {
            final URI item = result.get(i);
            final Set<URI> toAdd = resolvers.stream()
                                            .flatMap(r -> r.getRelatedVocabularies(item).stream())
                                            .collect(Collectors.toSet());
            result.forEach(toAdd::remove);
            result.addAll(toAdd);
        }
        // Skip the subject vocabulary
        return new HashSet<>(result.subList(1, result.size()));
    }
}
