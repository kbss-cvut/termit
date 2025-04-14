package cz.cvut.kbss.termit.persistence.relationship;

import jakarta.annotation.Nonnull;

import java.net.URI;
import java.util.Set;

/**
 * Resolves vocabulary relationships.
 */
public interface VocabularyRelationshipResolver {

    /**
     * Resolves the set of related vocabularies of the specified vocabulary.
     * <p>
     * It is expected that the resolution is recursive, i.e. not only directly related vocabularies are included.
     *
     * @param vocabulary Identifier of vocabulary whose related vocabularies to resolve
     * @return Set of related vocabulary identifiers
     */
    @Nonnull
    Set<URI> getRelatedVocabularies(@Nonnull URI vocabulary);
}
