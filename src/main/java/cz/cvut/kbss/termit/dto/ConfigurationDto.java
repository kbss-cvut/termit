package cz.cvut.kbss.termit.dto;

import cz.cvut.kbss.jopa.model.annotations.Id;
import cz.cvut.kbss.jopa.model.annotations.OWLAnnotationProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.termit.model.UserRole;
import cz.cvut.kbss.termit.util.Vocabulary;
import java.io.Serializable;
import java.net.URI;
import java.util.Set;

/**
 * Represents configuration data provided by the server to client.
 */
@OWLClass(iri = Vocabulary.s_c_konfigurace)
public class ConfigurationDto implements Serializable {

    @Id
    private URI id;

    @OWLAnnotationProperty(iri = DC.Terms.LANGUAGE)
    private String language;

    @OWLObjectProperty(iri = Vocabulary.s_p_ma_uzivatelskou_roli)
    private Set<UserRole> roles;

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public URI getId() {
        return id;
    }

    public void setId(URI id) {
        this.id = id;
    }

    public Set<UserRole> getRoles() {
        return roles;
    }

    public void setRoles(Set<UserRole> roles) {
        this.roles = roles;
    }
}
