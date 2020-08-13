package cz.cvut.kbss.termit.model.comment;

import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.termit.model.User;

import java.util.Objects;

@OWLClass(iri = "https://www.w3.org/ns/activitystreams#Like")
public class Like extends CommentReaction {

    public Like() {
    }

    public Like(User author, Comment comment) {
        setActor(Objects.requireNonNull(author).getUri());
        setObject(Objects.requireNonNull(comment).getUri());
    }

    @Override
    public String toString() {
        return "Like" + super.toString();
    }
}
