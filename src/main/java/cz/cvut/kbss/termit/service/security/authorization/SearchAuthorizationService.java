package cz.cvut.kbss.termit.service.security.authorization;

import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.search.FullTextSearchResult;
import cz.cvut.kbss.termit.model.Vocabulary;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Authorizes access to full text search results.
 */
@Service
public class SearchAuthorizationService {

    private final VocabularyAuthorizationService vocabularyAuthorizationService;

    public SearchAuthorizationService(VocabularyAuthorizationService vocabularyAuthorizationService) {
        this.vocabularyAuthorizationService = vocabularyAuthorizationService;
    }

    /**
     * Checks if the current user has read access to the specified full text search result.
     * <p>
     * Authorization is based on vocabulary ACL.
     *
     * @param instance Search result to authorize access to
     * @return {@code true} if the current user can read the specified instance, {@code false} otherwise
     */
    public boolean canRead(@NonNull FullTextSearchResult instance) {
        Objects.requireNonNull(instance);
        if (instance.getVocabulary() != null) {
            assert instance.hasType(SKOS.CONCEPT);
            return vocabularyAuthorizationService.canRead(new Vocabulary(instance.getVocabulary()));
        } else {
            assert instance.hasType(cz.cvut.kbss.termit.util.Vocabulary.s_c_slovnik);
            return vocabularyAuthorizationService.canRead(new Vocabulary(instance.getUri()));
        }
    }
}
