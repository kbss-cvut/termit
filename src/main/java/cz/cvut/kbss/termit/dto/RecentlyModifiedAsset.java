package cz.cvut.kbss.termit.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import cz.cvut.kbss.jopa.model.annotations.*;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.jopa.vocabulary.RDFS;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.io.Serializable;
import java.net.URI;
import java.util.*;

@SparqlResultSetMapping(name = "RecentlyModifiedAsset", classes = {@ConstructorResult(targetClass = RecentlyModifiedAsset.class,
        variables = {
                @VariableResult(name = "entity", type = URI.class),
                @VariableResult(name = "label", type = String.class),
                @VariableResult(name = "modified", type = Date.class),
                @VariableResult(name = "modifiedBy", type = URI.class),
                @VariableResult(name = "vocabulary", type = URI.class),
                @VariableResult(name = "type", type = String.class),
                @VariableResult(name = "changeType", type = String.class),
        })})
public class RecentlyModifiedAsset implements Serializable {

    @Id
    private URI uri;

    @OWLAnnotationProperty(iri = RDFS.LABEL)
    private String label;

    @OWLAnnotationProperty(iri = DC.Terms.MODIFIED)
    private Date modified;

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

    public RecentlyModifiedAsset(URI entity, String label, Date modified, URI modifiedBy, URI vocabulary, String type, String changeType) {
        this.uri = entity;
        this.label = label;
        this.modified = modified;
        this.modifiedBy = modifiedBy;
        this.vocabulary = vocabulary;
        this.types = new HashSet<>(Arrays.asList(type, changeType));
    }

    public URI getUri() {
        return uri;
    }

    public void setUri(URI uri) {
        this.uri = uri;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Date getModified() {
        return modified;
    }

    public void setModified(Date modified) {
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

    public Set<String> getTypes() {
        return types;
    }

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
