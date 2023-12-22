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
import cz.cvut.kbss.jopa.vocabulary.RDFS;
import cz.cvut.kbss.termit.model.comment.Comment;
import cz.cvut.kbss.termit.model.util.HasTypes;
import cz.cvut.kbss.termit.util.Utils;
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
            @VariableResult(name = "label", type = String.class),
            @VariableResult(name = "lastCommentUri", type = URI.class),
            @VariableResult(name = "myLastCommentUri", type = URI.class),
            @VariableResult(name = "vocabulary", type = URI.class),
            @VariableResult(name = "type", type = String.class)
        })})
public class RecentlyCommentedAsset implements HasTypes, Serializable {

    @Id
    private URI uri;

    @OWLAnnotationProperty(iri = RDFS.LABEL)
    private String label;

    @Transient
    private URI lastCommentUri;

    @OWLObjectProperty(iri = Vocabulary.s_p_je_tematem)
    private Comment lastComment;

    @Transient
    private URI myLastCommentUri;

    @OWLObjectProperty(iri = Vocabulary.s_p_ma_muj_posledni_komentar)
    private Comment myLastComment;

    @OWLObjectProperty(iri = Vocabulary.s_p_je_pojmem_ze_slovniku)
    private URI vocabulary;

    @Types
    private Set<String> types;

    public RecentlyCommentedAsset() {
    }

    public RecentlyCommentedAsset(URI entity, String label, URI lastCommentUri, URI myLastCommentUri, URI vocabulary, String type) {
        this.uri = entity;
        this.label = label;
        this.lastCommentUri = lastCommentUri;
        this.myLastCommentUri = myLastCommentUri;
        this.vocabulary = vocabulary;
        this.types = new HashSet<>(Collections.singleton(type));
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

    @Override
    public Set<String> getTypes() {
        return types;
    }

    @Override
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

    public URI getMyLastCommentUri() {
        return myLastCommentUri;
    }

    public void setMyLastCommentUri(URI myLastCommentUri) {
        this.myLastCommentUri = myLastCommentUri;
    }

    public Comment getMyLastComment() {
        return myLastComment;
    }

    public RecentlyCommentedAsset setMyLastComment(Comment myLastComment) {
        this.myLastComment = myLastComment;
        return this;
    }

    public URI getVocabulary() {
        return vocabulary;
    }

    public void setVocabulary(URI vocabulary) {
        this.vocabulary = vocabulary;
    }

    @Override
    public String toString() {
        return "RecentlyCommentedAsset{" +
                label + " " + Utils.uriToString(uri) +
            ", comment=" + lastComment +
            ", myLastComment=" + myLastComment +
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
            Objects.equals(myLastComment, that.myLastComment) &&
            Objects.equals(types, that.types);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri, lastComment, myLastComment, types);
    }
}
