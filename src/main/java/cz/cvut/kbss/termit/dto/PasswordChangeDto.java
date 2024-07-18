package cz.cvut.kbss.termit.dto;

import cz.cvut.kbss.jopa.model.annotations.Id;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLDataProperty;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.io.Serializable;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@OWLClass(iri = Vocabulary.ONTOLOGY_IRI_TERMIT + "/password-change")
public class PasswordChangeDto implements Serializable {
    @Id
    private URI uri;

    @OWLDataProperty(iri = DC.Terms.IDENTIFIER)
    private String token;

    @OWLDataProperty(iri = Vocabulary.s_p_ma_heslo)
    private String newPassword;

    public URI getUri() {
        return uri;
    }

    public void setUri(URI uri) {
        this.uri = uri;
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
