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
     *
     * @param vocabularyUri Vocabulary identifier
     * @return Repository context identifier
     */
    URI getVocabularyContext(URI vocabularyUri);
}
