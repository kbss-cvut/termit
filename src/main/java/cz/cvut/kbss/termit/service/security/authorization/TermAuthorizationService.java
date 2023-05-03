package cz.cvut.kbss.termit.service.security.authorization;

import cz.cvut.kbss.termit.model.AbstractTerm;
import cz.cvut.kbss.termit.model.Vocabulary;
import org.springframework.stereotype.Service;

import java.util.Objects;

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
}
