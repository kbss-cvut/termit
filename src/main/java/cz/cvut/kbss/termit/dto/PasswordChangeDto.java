package cz.cvut.kbss.termit.dto;

import java.io.Serializable;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class PasswordChangeDto implements Serializable {
    private URI uri;
    private String token;
    private String newPassword;

    public URI getUri() {
        return uri;
    }

    public void setUri(URI uri) {
        this.uri = uri;
    }

    public void setUri(String uri) {
        String stringURI = URLDecoder.decode(uri, StandardCharsets.UTF_8);
        setUri(URI.create(stringURI));
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
