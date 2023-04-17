package cz.cvut.kbss.termit.service.security.authorization;

import cz.cvut.kbss.termit.model.AbstractTerm;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.service.security.SecurityUtils;
import org.springframework.stereotype.Service;

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
     *
     * @param target Owner of the new term
     * @return {@code true} if the current user is authorized to create term in the specified vocabulary, {@code false}
     * otherwise
     */
    public boolean canCreateIn(Vocabulary target) {
        return isUserAtLeastEditor() && vocabularyAuthorizationService.canModify(target);
    }

    private boolean isUserAtLeastEditor() {
        final UserAccount user = SecurityUtils.currentUser();
        return user.isAdmin() || user.hasType(cz.cvut.kbss.termit.util.Vocabulary.s_c_plny_uzivatel_termitu);
    }

    @Override
    public boolean canView(AbstractTerm asset) {
        return vocabularyAuthorizationService.canView(getVocabulary(asset));
    }

    private Vocabulary getVocabulary(AbstractTerm term) {
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
