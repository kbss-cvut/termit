/*
 * TermIt
 * Copyright (C) 2023 Czech Technical University in Prague
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
package cz.cvut.kbss.termit.service.security.authorization;

import cz.cvut.kbss.jopa.vocabulary.SKOS;
import cz.cvut.kbss.termit.dto.search.FacetedSearchResult;
import cz.cvut.kbss.termit.dto.search.FullTextSearchResult;
import cz.cvut.kbss.termit.model.Vocabulary;
import jakarta.annotation.Nonnull;
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
    public boolean canRead(@Nonnull FullTextSearchResult instance) {
        Objects.requireNonNull(instance);
        if (instance.getVocabulary() != null) {
            assert instance.hasType(SKOS.CONCEPT);
            return vocabularyAuthorizationService.canRead(new Vocabulary(instance.getVocabulary()));
        } else {
            assert instance.hasType(cz.cvut.kbss.termit.util.Vocabulary.s_c_slovnik);
            return vocabularyAuthorizationService.canRead(new Vocabulary(instance.getUri()));
        }
    }

    /**
     * Checks if the current user has read access to the specified faceted term search result.
     * <p>
     * Authorization is based on vocabulary ACL.
     *
     * @param instance Faceted search result to authorize access to
     * @return {@code true} if the current user can read the specified instance, {@code false} otherwise
     */
    public boolean canRead(@Nonnull FacetedSearchResult instance) {
        Objects.requireNonNull(instance);
        return vocabularyAuthorizationService.canRead(new Vocabulary(instance.getVocabulary()));
    }
}
