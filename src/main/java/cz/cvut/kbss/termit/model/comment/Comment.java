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
package cz.cvut.kbss.termit.model.comment;

import cz.cvut.kbss.jopa.model.annotations.*;
import cz.cvut.kbss.termit.model.AbstractEntity;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.util.Utils;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a single comment in the comment/discussion module.
 */
@OWLClass(iri = Vocabulary.s_c_Comment)
public class Comment extends AbstractEntity {

    /**
     * The asset to which this comment has been made.
     */
    @ParticipationConstraints(nonEmpty = true)
    @OWLObjectProperty(iri = Vocabulary.s_p_topic)
    private URI asset;

    @ParticipationConstraints(nonEmpty = true)
    @OWLDataProperty(iri = Vocabulary.s_p_content_A)
    private String content;

    @ParticipationConstraints(nonEmpty = true)
    @OWLObjectProperty(iri = Vocabulary.s_p_has_creator, fetch = FetchType.EAGER)
    private User author;

    @ParticipationConstraints(nonEmpty = true)
    @OWLDataProperty(iri = Vocabulary.s_p_ma_datum_a_cas_vytvoreni)
    private Instant created;

    @OWLDataProperty(iri = Vocabulary.s_p_ma_datum_a_cas_posledni_modifikace)
    private Instant modified;

    @Inferred
    @OWLObjectProperty(iri = Vocabulary.s_p_ma_reakci, cascade = {CascadeType.REMOVE}, fetch = FetchType.EAGER)
    private Set<CommentReaction> reactions;

    public URI getAsset() {
        return asset;
    }

    public void setAsset(URI asset) {
        this.asset = asset;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public User getAuthor() {
        return author;
    }

    public void setAuthor(User author) {
        this.author = author;
    }

    public Instant getCreated() {
        return created;
    }

    public void setCreated(Instant created) {
        this.created = created;
    }

    public Instant getModified() {
        return modified;
    }

    public void setModified(Instant modified) {
        this.modified = modified;
    }

    public Set<CommentReaction> getReactions() {
        return reactions;
    }

    public void setReactions(Set<CommentReaction> reactions) {
        this.reactions = reactions;
    }

    @PrePersist
    public void prePersist() {
        setCreated(Utils.timestamp());
    }

    @PreUpdate
    public void preUpdate() {
        setModified(Utils.timestamp());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Comment)) {
            return false;
        }
        Comment comment = (Comment) o;
        return Objects.equals(asset, comment.asset) &&
                Objects.equals(content, comment.content) &&
                Objects.equals(author, comment.author) &&
                Objects.equals(created, comment.created);
    }

    @Override
    public int hashCode() {
        return Objects.hash(asset, content, author, created);
    }

    @Override
    public String toString() {
        return "Comment{" +
                super.toString() +
                ", asset=<" + asset + '>' +
                ", content='" + content.substring(0, Math.min(50, content.length())) + '\'' +
                ", author=" + author +
                ", created=" + created +
                "}";
    }
}
