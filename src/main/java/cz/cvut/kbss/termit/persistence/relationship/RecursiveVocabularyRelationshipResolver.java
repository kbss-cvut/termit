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
