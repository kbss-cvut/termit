package cz.cvut.kbss.termit.dto;

import cz.cvut.kbss.jopa.model.annotations.ConstructorResult;
import cz.cvut.kbss.jopa.model.annotations.Id;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.model.annotations.SparqlResultSetMapping;
import cz.cvut.kbss.jopa.model.annotations.Transient;
import cz.cvut.kbss.jopa.model.annotations.Types;
import cz.cvut.kbss.jopa.model.annotations.VariableResult;
import cz.cvut.kbss.termit.model.comment.Comment;
import cz.cvut.kbss.termit.util.Vocabulary;
import java.io.Serializable;
import java.net.URI;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@SparqlResultSetMapping(name = "RecentlyCommentedAsset",
    classes = {@ConstructorResult(targetClass = RecentlyCommentedAsset.class,
        variables = {
            @VariableResult(name = "entity", type = URI.class),
            @VariableResult(name = "lastCommentUri", type = URI.class),
            @VariableResult(name = "lastMyCommentUri", type = URI.class),
            @VariableResult(name = "type", type = String.class)
        })})
public class RecentlyCommentedAsset implements Serializable {

    @Id
    private URI uri;

    @Transient
    private URI lastCommentUri;

    @OWLObjectProperty(iri = Vocabulary.s_p_je_tematem)
    private Comment lastComment;

    @Transient
    private URI lastMyCommentUri;

    @OWLObjectProperty(iri = Vocabulary.s_p_ma_muj_posledni_komentar)
    private Comment lastMyComment;

    @Types
    private Set<String> types;

    public RecentlyCommentedAsset() {
    }

    public RecentlyCommentedAsset(URI entity, URI lastCommentUri, URI lastMyCommentUri, String type) {
        this.uri = entity;
        this.lastCommentUri = lastCommentUri;
        this.lastMyCommentUri = lastMyCommentUri;
        this.types = new HashSet<>(Collections.singleton(type));
    }

    public URI getUri() {
        return uri;
    }

    public void setUri(URI uri) {
        this.uri = uri;
    }

    public Set<String> getTypes() {
        return types;
    }

    public void setTypes(Set<String> types) {
        this.types = types;
    }

    public URI getLastCommentUri() {
        return lastCommentUri;
    }

    public void setLastCommentUri(URI lastCommentUri) {
        this.lastCommentUri = lastCommentUri;
    }

    public Comment getLastComment() {
        return lastComment;
    }

    public RecentlyCommentedAsset setLastComment(Comment lastComment) {
        this.lastComment = lastComment;
        return this;
    }

    public URI getLastMyCommentUri() {
        return lastMyCommentUri;
    }

    public void setLastMyCommentUri(URI lastMyCommentUri) {
        this.lastMyCommentUri = lastMyCommentUri;
    }

    public Comment getLastMyComment() {
        return lastMyComment;
    }

    public RecentlyCommentedAsset setLastMyComment(Comment lastMyComment) {
        this.lastMyComment = lastMyComment;
        return this;
    }

    @Override
    public String toString() {
        return "RecentlyCommentedAsset{" +
            "uri=" + uri +
            ", comment=" + lastComment.toString() +
            ", types=" + types +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RecentlyCommentedAsset)) {
            return false;
        }
        RecentlyCommentedAsset that = (RecentlyCommentedAsset) o;
        return Objects.equals(uri, that.uri) &&
            Objects.equals(lastComment, that.lastComment) &&
            Objects.equals(lastMyComment, that.lastMyComment) &&
            Objects.equals(types, that.types);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri, lastComment, lastMyComment, types);
    }
}
