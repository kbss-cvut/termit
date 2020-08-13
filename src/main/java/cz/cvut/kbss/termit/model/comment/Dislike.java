package cz.cvut.kbss.termit.model.comment;

import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.termit.model.User;

import java.util.Objects;

@OWLClass(iri = "http://onto.fel.cvut.cz/ontologies/application/termit/pojem/palec-dolů")
public class Dislike extends CommentReaction {

    public Dislike() {
    }

    public Dislike(User author, Comment comment) {
        setActor(Objects.requireNonNull(author).getUri());
        setObject(Objects.requireNonNull(comment).getUri());
    }

    @Override
    public String toString() {
        return "Dislike" + super.toString();
    }
}
