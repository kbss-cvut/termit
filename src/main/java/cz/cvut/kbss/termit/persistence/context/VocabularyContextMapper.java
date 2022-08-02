package cz.cvut.kbss.termit.persistence.context;

import cz.cvut.kbss.termit.model.Vocabulary;

import java.net.URI;
import java.util.Objects;

/**
 * Maps vocabularies to repository contexts in which they are stored.
 */
public interface VocabularyContextMapper {

    /**
     * Gets identifier of the repository context in which the specified vocabulary is stored.
     *
     * @param vocabulary Vocabulary whose context to retrieve
     * @return Repository context identifier
     */
    default URI getVocabularyContext(Vocabulary vocabulary) {
        Objects.requireNonNull(vocabulary);
        return getVocabularyContext(vocabulary.getUri());
    }

    /**
     * Gets identifier of the repository context in which vocabulary with the specified identifier is stored.
     * <p>
     * If the vocabulary does not exist yet (and thus has no repository context), the vocabulary identifier is returned
     * as context.
     *
     * @param vocabularyUri Vocabulary identifier
     * @return Repository context identifier
     * @throws cz.cvut.kbss.termit.exception.AmbiguousVocabularyContextException In case multiple contexts for a
     *                                                                           vocabulary are found
     */
    URI getVocabularyContext(URI vocabularyUri);
}
