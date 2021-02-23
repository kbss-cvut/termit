package cz.cvut.kbss.termit.dto;

import cz.cvut.kbss.jopa.model.annotations.Id;
import cz.cvut.kbss.jopa.model.annotations.OWLAnnotationProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.termit.model.UserRole;
import cz.cvut.kbss.termit.util.Vocabulary;
import java.io.Serializable;
import java.net.URL;
import java.util.Set;

/**
 * Represents configuration data provided by the server to client.
 */
@OWLClass(iri = Vocabulary.s_c_konfigurace)
public class ConfigurationDto implements Serializable {

    @Id
    private URL id;

    @OWLAnnotationProperty(iri = Vocabulary.s_p_language)
    private String language;

    @OWLObjectProperty(iri = Vocabulary.s_p_ma_uzivatelskou_roli)
    private Set<UserRole> roles;

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public URL getId() {
        return id;
    }

    public void setId(URL id) {
        this.id = id;
    }

    public Set<UserRole> getRoles() {
        return roles;
    }

    public void setRoles(Set<UserRole> roles) {
        this.roles = roles;
    }
}
