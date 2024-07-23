package cz.cvut.kbss.termit.dto;

import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.model.util.HasIdentifier;

import java.io.Serializable;
import java.net.URI;
import java.time.Instant;

public class PasswordChangeRequestDto implements Serializable, HasIdentifier {
    private URI uri;

    /**
     * Associated user account.
     */
    private UserAccount userAccount;

    /**
     * Token value.
     */
    private String token;

    /**
     * Token creation timestamp.
     */
    private Instant createdAt;

    @Override
    public URI getUri() {
        return uri;
    }

    @Override
    public void setUri(URI uri) {
        this.uri = uri;
    }

    public UserAccount getUserAccount() {
        return userAccount;
    }

    public void setUserAccount(UserAccount userAccount) {
        this.userAccount = userAccount;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
