package cz.cvut.kbss.termit.model.comment;

import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.model.annotations.ParticipationConstraints;
import cz.cvut.kbss.termit.model.AbstractEntity;

import java.net.URI;
import java.util.Objects;

@OWLClass(iri = "https://www.w3.org/ns/activitystreams#Like")
public class Like extends AbstractEntity {

    @ParticipationConstraints(nonEmpty = true)
    @OWLObjectProperty(iri = "https://www.w3.org/ns/activitystreams#actor")
    private URI actor;

    @ParticipationConstraints(nonEmpty = true)
    @OWLObjectProperty(iri = "https://www.w3.org/ns/activitystreams#object")
    private URI object;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Like)) {
            return false;
        }
        Like like = (Like) o;
        return Objects.equals(actor, like.actor) && Objects.equals(object, like.object);
    }

    @Override
    public int hashCode() {
        return Objects.hash(actor, object);
    }

    @Override
    public String toString() {
        return "Like{" +
                "actor=" + actor +
                ", object=" + object +
                "}";
    }
}
