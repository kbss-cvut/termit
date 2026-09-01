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
package cz.cvut.kbss.termit.service.security.authorization;

import cz.cvut.kbss.termit.model.AbstractTerm;
import cz.cvut.kbss.termit.model.TermInfoWithParents;
import cz.cvut.kbss.termit.model.Vocabulary;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Authorizes access to terms.
 * <p>
 * This access is mostly guided by access rules of the vocabulary that contains the term.
 */
@Service
public class TermAuthorizationService implements AssetAuthorizationService<AbstractTerm> {

    private final VocabularyAuthorizationService vocabularyAuthorizationService;

    public TermAuthorizationService(VocabularyAuthorizationService vocabularyAuthorizationService) {
        this.vocabularyAuthorizationService = vocabularyAuthorizationService;
    }

    /**
     * Checks if the current user can create a term in the specified target vocabulary.
     * <p>
     * A user is authorized to create a term if they are authorized to modify a vocabulary.
     *
     * @param target Owner of the new term
     * @return {@code true} if the current user is authorized to create term in the specified vocabulary, {@code false}
     * otherwise
     */
    public boolean canCreateIn(Vocabulary target) {
        return vocabularyAuthorizationService.canModify(target);
    }

    /**
     * Checks if the current user can create a child of the specified term.
     * <p>
     * A user is authorized to crate a child term of a term if they are authorized to modify the vocabulary that
     * contains the parent term.
     *
     * @param parent Parent term of the new term
     * @return {@code true} if the current user is authorized to create term in the parent term's vocabulary, {@code
     * false} otherwise
     */
    public boolean canCreateChild(AbstractTerm parent) {
        return canCreateIn(getVocabulary(parent));
    }

    private Vocabulary getVocabulary(AbstractTerm term) {
        Objects.requireNonNull(term);
        return new Vocabulary(term.getVocabulary());
    }

    @Override
    public boolean canRead(AbstractTerm asset) {
        return vocabularyAuthorizationService.canRead(getVocabulary(asset));
    }

    @Override
    public boolean canModify(AbstractTerm asset) {
        return vocabularyAuthorizationService.canModify(getVocabulary(asset));
    }

    @Override
    public boolean canRemove(AbstractTerm asset) {
        return vocabularyAuthorizationService.canRemove(getVocabulary(asset));
    }

    /**
     * Removes all terms from the collection in-place including their ancestors
     * if the current user lacks authorization to read their associated vocabulary.
     *
     * @param terms mutable collection of terms to filter
     */
    public void removeUnauthorizedTermsAndAncestors(Collection<TermInfoWithParents> terms) {
        Set<URI> vocabularies = new HashSet<>();
        collectAllAncestorVocabularies(terms, vocabularies);

        // remove vocabulary if user CAN access it
        vocabularies.removeIf(vocabulary -> vocabularyAuthorizationService.canRead(new Vocabulary(vocabulary)));
        // only unauthorized vocabularies left

        removeTermsFromVocabularies(terms, vocabularies);
    }

    /**
     * Collects all vocabulary identifiers of the terms and their ancestors into the {@code vocabularies} set
     *
     * @param terms terms from which vocabularies should be collected
     * @param vocabularies modifiable set of vocabulary identifiers that should be filled
     */
    private void collectAllAncestorVocabularies(Collection<TermInfoWithParents> terms, Set<URI> vocabularies) {
        if (terms == null) {
            return;
        }
        terms.forEach(term -> {
            if (term.getVocabulary() != null) {
                vocabularies.add(term.getVocabulary());
            }
            collectAllAncestorVocabularies(term.getParentTerms(), vocabularies);
        });
    }

    /**
     * Recursively remove terms and their ancestors from the mutable collection
     * if they belong in one of {@code unauthorizedVocabularies}.
     *
     * @param terms mutable collection of terms to filter
     * @param unauthorizedVocabularies vocabulary identifiers to exclude
     */
    private void removeTermsFromVocabularies(Collection<TermInfoWithParents> terms, Set<URI> unauthorizedVocabularies) {
        if (terms == null) {
            return;
        }

        terms.removeIf(term -> unauthorizedVocabularies.contains(term.getVocabulary()));
        terms.forEach(term -> {
            try {
                removeTermsFromVocabularies(term.getParentTerms(), unauthorizedVocabularies);
            } catch (UnsupportedOperationException e) {
                term.setParentTerms(new HashSet<>(term.getParentTerms()));
                removeTermsFromVocabularies(term.getParentTerms(), unauthorizedVocabularies);
            }
        });
    }
}
