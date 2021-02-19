package cz.cvut.kbss.termit.model.comment;

import cz.cvut.kbss.jopa.model.annotations.*;
import cz.cvut.kbss.termit.model.AbstractEntity;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.net.URI;
import java.util.Date;
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
    @OWLDataProperty(iri = Vocabulary.s_p_content)
    private String content;

    @ParticipationConstraints(nonEmpty = true)
    @OWLObjectProperty(iri = Vocabulary.s_p_has_creator, fetch = FetchType.EAGER)
    private User author;

    @ParticipationConstraints(nonEmpty = true)
    @OWLDataProperty(iri = Vocabulary.s_p_ma_datum_a_cas_vytvoreni)
    private Date created;

    @OWLDataProperty(iri = Vocabulary.s_p_ma_datum_a_cas_posledni_modifikace)
    private Date modified;

    @Inferred
    @OWLObjectProperty(iri = Vocabulary.s_p_ma_reakci, fetch = FetchType.EAGER)
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

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public Date getModified() {
        return modified;
    }

    public void setModified(Date modified) {
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
        this.created = new Date();
    }

    @PreUpdate
    public void preUpdate() {
        this.modified = new Date();
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
