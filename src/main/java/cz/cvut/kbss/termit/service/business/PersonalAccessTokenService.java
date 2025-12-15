package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.termit.dto.PersonalAccessTokenDto;
import cz.cvut.kbss.termit.exception.AuthorizationException;
import cz.cvut.kbss.termit.exception.TokenExpiredException;
import cz.cvut.kbss.termit.model.PersonalAccessToken;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.service.repository.PersonalAccessTokenRepositoryService;
import cz.cvut.kbss.termit.service.security.SecurityUtils;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
public class PersonalAccessTokenService {
    private final PersonalAccessTokenRepositoryService repositoryService;
    private final SecurityUtils securityUtils;

    public PersonalAccessTokenService(PersonalAccessTokenRepositoryService repositoryService,
                                      SecurityUtils securityUtils) {
        this.repositoryService = repositoryService;
        this.securityUtils = securityUtils;
    }

    /**
     * Finds all personal access tokens associated with the currently logged-in user.
     * @return personal access tokens
     */
    public List<PersonalAccessTokenDto> findAllForCurrentUser() {
        final UserAccount currentUser = securityUtils.getCurrentUser();
        return repositoryService.findAllByUserAccount(currentUser);
    }

    /**
     * Finds the user account associated with the given token.
     * @param tokenUri The identifier of the token
     * @return Associated user account or null if the token was not found
     * @throws TokenExpiredException when the token is expired
     */
    public UserAccount findUserAccountByTokenId(URI tokenUri) {
        return repositoryService.find(tokenUri)
                                .map(this::ensureTokenValid)
                                .map(PersonalAccessToken::getOwner)
                                .orElse(null);
    }

    /**
     * Creates a new personal access token with the specified expiration date.
     * @param expirationDate Expiration date or null for unlimited lifetime.
     * @return The created personal access token
     */
    public PersonalAccessTokenDto create(LocalDate expirationDate) {
        final UserAccount currentUser = securityUtils.getCurrentUser();
        final PersonalAccessToken newToken = new PersonalAccessToken();
        newToken.setExpirationDate(expirationDate);
        newToken.setOwner(currentUser);

        repositoryService.persist(newToken);
        return repositoryService.mapToDto(newToken);
    }

    /**
     * Deletes token with the specified identifier associated with the current user.
     * @param tokenUri the token identifier
     * @throws AuthorizationException when attempted to delete the token from a different user
     */
    public void delete(URI tokenUri) {
        Objects.requireNonNull(tokenUri);
        final PersonalAccessToken token = repositoryService.findRequired(tokenUri);
        Objects.requireNonNull(token.getOwner());
        final UserAccount currentUser = securityUtils.getCurrentUser();
        if (currentUser.equals(token.getOwner())) {
            repositoryService.remove(token);
            return;
        }
        throw new AuthorizationException("Cannot delete token associated with a different user.");
    }

    public PersonalAccessToken ensureTokenValid(PersonalAccessToken token) {
        Objects.requireNonNull(token);
        Objects.requireNonNull(token.getUri());
        Objects.requireNonNull(token.getOwner());
        if (LocalDate.now().isAfter(token.getExpirationDate())) {
            throw new TokenExpiredException("Personal access token expired");
        }
        return token;
    }
}
