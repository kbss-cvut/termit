package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.termit.dto.PersonalAccessTokenDto;
import cz.cvut.kbss.termit.exception.AuthorizationException;
import cz.cvut.kbss.termit.exception.TokenExpiredException;
import cz.cvut.kbss.termit.model.PersonalAccessToken;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.security.JwtUtils;
import cz.cvut.kbss.termit.service.repository.PersonalAccessTokenRepositoryService;
import cz.cvut.kbss.termit.service.security.SecurityUtils;
import cz.cvut.kbss.termit.util.Utils;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
public class PersonalAccessTokenService {
    private final PersonalAccessTokenRepositoryService repositoryService;
    private final SecurityUtils securityUtils;
    private final JwtUtils jwtUtils;

    public PersonalAccessTokenService(PersonalAccessTokenRepositoryService repositoryService,
                                      SecurityUtils securityUtils, JwtUtils jwtUtils) {
        this.repositoryService = repositoryService;
        this.securityUtils = securityUtils;
        this.jwtUtils = jwtUtils;
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
     * Finds the user account associated with the given valid token.
     * @param tokenUri The identifier of the token
     * @return Associated user account
     * @throws TokenExpiredException when the token is expired
     */
    public UserAccount findUserAccountByValidTokenId(URI tokenUri) {
        return repositoryService.find(tokenUri)
                                .map(this::ensureTokenValid)
                                .map(PersonalAccessToken::getOwner)
                                .orElseThrow();
    }

    /**
     * Creates a new personal access token with the specified expiration date.
     * @param expirationDate Expiration date or null for unlimited lifetime.
     * @return The created personal access token
     */
    public String create(LocalDate expirationDate) {
        final UserAccount currentUser = securityUtils.getCurrentUser();
        final PersonalAccessToken newToken = new PersonalAccessToken();
        newToken.setCreated(Utils.timestamp());
        newToken.setExpirationDate(expirationDate);
        newToken.setOwner(currentUser);

        repositoryService.persist(newToken);
        return jwtUtils.generatePAT(newToken);
    }

    /**
     * Deletes token with the specified identifier associated with the current user.
     * @param tokenUri the token identifier
     * @throws AuthorizationException when attempted to delete the token from a different user
     */
    public void delete(URI tokenUri) {
        Objects.requireNonNull(tokenUri, "Token URI must not be null");
        final PersonalAccessToken token = repositoryService.findRequired(tokenUri);
        Objects.requireNonNull(token.getOwner(), "Token owner must not be null");
        final UserAccount currentUser = securityUtils.getCurrentUser();
        if (currentUser.equals(token.getOwner())) {
            repositoryService.remove(token);
            return;
        }
        throw new AuthorizationException("Cannot delete token associated with a different user.");
    }

    public PersonalAccessToken ensureTokenValid(PersonalAccessToken token) {
        Objects.requireNonNull(token, "Token must not be null");
        Objects.requireNonNull(token.getUri(), "Token URI must not be null");
        Objects.requireNonNull(token.getOwner(), "Token owner must not be null");
        if (token.getExpirationDate() != null && LocalDate.now().isAfter(token.getExpirationDate())) {
            throw new TokenExpiredException("Personal access token expired");
        }
        return token;
    }
}
