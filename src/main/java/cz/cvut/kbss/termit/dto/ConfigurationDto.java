package cz.cvut.kbss.termit.dto;

import cz.cvut.kbss.jopa.model.annotations.*;
import cz.cvut.kbss.jopa.model.annotations.util.NonEntity;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.termit.model.UserRole;
import cz.cvut.kbss.termit.model.acl.AccessLevel;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.io.Serializable;
import java.net.URI;
import java.util.List;
import java.util.Set;

/**
 * Represents configuration data provided by the server to client.
 */
@NonEntity
@OWLClass(iri = Vocabulary.s_c_konfigurace)
public class ConfigurationDto implements Serializable {

    @Id
    private URI id;

    @OWLAnnotationProperty(iri = DC.Terms.LANGUAGE)
    private String language;

    @OWLObjectProperty(iri = Vocabulary.s_p_ma_uzivatelskou_roli)
    private Set<UserRole> roles;

    @OWLDataProperty(iri = Vocabulary.s_p_ma_maximalni_velikost_souboru)
    private String maxFileUploadSize;

    @OWLDataProperty(iri = Vocabulary.s_p_ma_oddelovac_verze)
    private String versionSeparator;

    @OWLObjectProperty(iri = Vocabulary.s_p_ma_uroven_pristupovych_opravneni)
    private Set<AccessLevel> accessLevels;

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

    public String getMaxFileUploadSize() {
        return maxFileUploadSize;
    }

    public void setMaxFileUploadSize(String maxFileUploadSize) {
        this.maxFileUploadSize = maxFileUploadSize;
    }

    public String getVersionSeparator() {
        return versionSeparator;
    }

    public void setVersionSeparator(String versionSeparator) {
        this.versionSeparator = versionSeparator;
    }

    public Set<AccessLevel> getAccessLevels() {
        return accessLevels;
    }

    public void setAccessLevels(Set<AccessLevel> accessLevels) {
        this.accessLevels = accessLevels;
    }
}
