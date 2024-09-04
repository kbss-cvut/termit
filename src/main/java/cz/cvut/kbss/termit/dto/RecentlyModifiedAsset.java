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

import com.fasterxml.jackson.annotation.JsonIgnore;
import cz.cvut.kbss.jopa.model.annotations.ConstructorResult;
import cz.cvut.kbss.jopa.model.annotations.Id;
import cz.cvut.kbss.jopa.model.annotations.OWLAnnotationProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.model.annotations.SparqlResultSetMapping;
import cz.cvut.kbss.jopa.model.annotations.Types;
import cz.cvut.kbss.jopa.model.annotations.VariableResult;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.jopa.vocabulary.RDFS;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.model.util.HasTypes;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.io.Serializable;
import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@SparqlResultSetMapping(name = "RecentlyModifiedAsset", classes = {@ConstructorResult(targetClass = RecentlyModifiedAsset.class,
        variables = {
                @VariableResult(name = "entity", type = URI.class),
                @VariableResult(name = "label", type = String.class),
                @VariableResult(name = "modified", type = Instant.class),
                @VariableResult(name = "modifiedBy", type = URI.class),
                @VariableResult(name = "vocabulary", type = URI.class),
                @VariableResult(name = "type", type = String.class),
                @VariableResult(name = "changeType", type = String.class),
        })})
public class RecentlyModifiedAsset implements HasIdentifier, HasTypes, Serializable {

    @Id
    private URI uri;

    @OWLAnnotationProperty(iri = RDFS.LABEL)
    private String label;

    @OWLAnnotationProperty(iri = DC.Terms.MODIFIED)
    private Instant modified;

    @JsonIgnore
    private transient URI modifiedBy;

    @OWLObjectProperty(iri = Vocabulary.s_p_ma_editora)
    private User editor;

    // In case the modified asset is a term, we want its vocabulary as well
    @OWLObjectProperty(iri = Vocabulary.s_p_je_pojmem_ze_slovniku)
    private URI vocabulary;

    @Types
    private Set<String> types;

    public RecentlyModifiedAsset() {
    }

    public RecentlyModifiedAsset(URI entity, String label, Instant modified, URI modifiedBy, URI vocabulary, String type, String changeType) {
        this.uri = entity;
        this.label = label;
        this.modified = modified;
        this.modifiedBy = modifiedBy;
        this.vocabulary = vocabulary;
        this.types = new HashSet<>(Arrays.asList(type, changeType));
    }

    @Override
    public URI getUri() {
        return uri;
    }

    @Override
    public void setUri(URI uri) {
        this.uri = uri;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Instant getModified() {
        return modified;
    }

    public void setModified(Instant modified) {
        this.modified = modified;
    }

    public URI getModifiedBy() {
        return modifiedBy;
    }

    public void setModifiedBy(URI modifiedBy) {
        this.modifiedBy = modifiedBy;
    }

    public User getEditor() {
        return editor;
    }

    public void setEditor(User editor) {
        this.editor = editor;
    }

    public URI getVocabulary() {
        return vocabulary;
    }

    public void setVocabulary(URI vocabulary) {
        this.vocabulary = vocabulary;
    }

    @Override
    public Set<String> getTypes() {
        return types;
    }

    @Override
    public void setTypes(Set<String> types) {
        this.types = types;
    }

    @Override
    public String toString() {
        return "RecentlyModifiedAsset{" +
                "uri=" + uri +
                ", label='" + label + '\'' +
                ", modified=" + modified +
                ", types=" + types +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RecentlyModifiedAsset)) {
            return false;
        }
        RecentlyModifiedAsset that = (RecentlyModifiedAsset) o;
        return Objects.equals(uri, that.uri) &&
                Objects.equals(label, that.label) &&
                Objects.equals(modified, that.modified) &&
                Objects.equals(vocabulary, that.vocabulary) &&
                Objects.equals(types, that.types);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri, label, modified, vocabulary, types);
    }
}
