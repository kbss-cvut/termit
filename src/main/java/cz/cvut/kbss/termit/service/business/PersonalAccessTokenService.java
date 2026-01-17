package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.termit.dto.PersonalAccessTokenDto;
import cz.cvut.kbss.termit.exception.AuthorizationException;
import cz.cvut.kbss.termit.exception.JwtException;
import cz.cvut.kbss.termit.exception.TokenExpiredException;
import cz.cvut.kbss.termit.model.PersonalAccessToken;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.security.JwtUtils;
import cz.cvut.kbss.termit.service.repository.PersonalAccessTokenRepositoryService;
import cz.cvut.kbss.termit.service.security.SecurityUtils;
import cz.cvut.kbss.termit.util.Utils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.Validator;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
public class PersonalAccessTokenService {
    private final PersonalAccessTokenRepositoryService repositoryService;
    private final SecurityUtils securityUtils;
    private final JwtUtils jwtUtils;
    private final Validator validator;

    public PersonalAccessTokenService(PersonalAccessTokenRepositoryService repositoryService,
                                      SecurityUtils securityUtils, JwtUtils jwtUtils, Validator validator) {
        this.repositoryService = repositoryService;
        this.securityUtils = securityUtils;
        this.jwtUtils = jwtUtils;
        this.validator = validator;
    }

    /**
     * Finds all personal access tokens associated with the currently logged-in user.
     *
     * @return personal access tokens
     */
    public List<PersonalAccessTokenDto> findAllForCurrentUser() {
        final UserAccount currentUser = securityUtils.getCurrentUser();
        return repositoryService.findAllByUserAccount(currentUser);
    }

    /**
     * Finds the token and ensures that its valid.
     *
     * @param tokenUri The identifier of the token
     * @return The valid personal access token
     * @throws TokenExpiredException when the token is invalid (e.g. expired)
     */
    public PersonalAccessToken findValid(URI tokenUri) {
        return repositoryService.find(tokenUri)
                                .map(this::ensureTokenValid)
                                .orElseThrow();
    }

    /**
     * Creates a new personal access token with the specified expiration date.
     *
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
     *
     * @param tokenUri the token identifier
     * @throws AuthorizationException when attempted to delete the token from a different user
     */
    @Transactional
    public void delete(URI tokenUri) {
        Objects.requireNonNull(tokenUri, "Token URI must not be null");
        final PersonalAccessToken token = repositoryService.findRequired(tokenUri);
        Objects.requireNonNull(token.getOwner(), "Token owner must not be null");
        final UserAccount currentUser = securityUtils.getCurrentUser();
        if (token.getOwner().equals(currentUser)) {
            repositoryService.remove(token);
            return;
        }
        throw new AuthorizationException("Cannot delete token associated with a different user.");
    }

    /**
     * Ensures the PAT is valid (non-expired).
     *
     * @param token the token to validate
     * @return the valid token
     * @throws TokenExpiredException when the token is expired
     * @throws NullPointerException  when a required field is null
     */
    public PersonalAccessToken ensureTokenValid(PersonalAccessToken token) {
        validator.validateObject(token)
                 .failOnError((err) -> new JwtException("Invalid PAT token: " + err));
        return token;
    }

    public void updateLastUsed(PersonalAccessToken token) {
        token.setLastUsed(Utils.timestamp());
        repositoryService.update(token);
    }
}
