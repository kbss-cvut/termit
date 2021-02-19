package cz.cvut.kbss.termit.model.comment;

import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.model.annotations.ParticipationConstraints;
import cz.cvut.kbss.jopa.model.annotations.Types;
import cz.cvut.kbss.termit.model.AbstractEntity;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.util.HasTypes;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.net.URI;
import java.util.Objects;
import java.util.Set;

@OWLClass(iri = Vocabulary.s_c_reakce)
public class CommentReaction extends AbstractEntity implements HasTypes {

    @ParticipationConstraints(nonEmpty = true)
    @OWLObjectProperty(iri = Vocabulary.s_p_actor)
    private URI actor;

    @ParticipationConstraints(nonEmpty = true)
    @OWLObjectProperty(iri = Vocabulary.s_p_object)
    private URI object;

    @ParticipationConstraints(nonEmpty = true)
    @Types
    private Set<String> types;

    public CommentReaction() {
    }

    public CommentReaction(User author, Comment comment) {
        this.actor = Objects.requireNonNull(author).getUri();
        this.object = Objects.requireNonNull(comment).getUri();
    }

    public URI getActor() {
        return actor;
    }

    public void setActor(URI actor) {
        this.actor = actor;
    }

    public URI getObject() {
        return object;
    }

    public void setObject(URI object) {
        this.object = object;
    }

    public Set<String> getTypes() {
        return types;
    }

    public void setTypes(Set<String> types) {
        this.types = types;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CommentReaction)) {
            return false;
        }
        CommentReaction reaction = (CommentReaction) o;
        return Objects.equals(actor, reaction.actor) &&
                Objects.equals(object, reaction.object) &&
                Objects.equals(types, reaction.types);
    }

    @Override
    public int hashCode() {
        return Objects.hash(actor, object, types);
    }

    @Override
    public String toString() {
        return "CommentReaction{" +
                "actor=" + actor +
                ", object=" + object +
                ", types=" + types +
                "}";
    }
}
