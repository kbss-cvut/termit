/*
 * TermIt
 * Copyright (C) 2023 Czech Technical University in Prague
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.dto;

import cz.cvut.kbss.jopa.model.annotations.*;
import cz.cvut.kbss.jopa.model.annotations.util.NonEntity;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.termit.model.UserRole;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.io.Serializable;
import java.net.URI;
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
}
