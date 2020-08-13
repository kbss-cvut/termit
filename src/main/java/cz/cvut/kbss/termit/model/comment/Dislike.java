package cz.cvut.kbss.termit.model.comment;

import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.model.annotations.ParticipationConstraints;
import cz.cvut.kbss.termit.model.AbstractEntity;

import java.net.URI;
import java.util.Objects;

@OWLClass(iri = "http://onto.fel.cvut.cz/ontologies/application/termit/pojem/palec-dolů")
public class Dislike extends AbstractEntity {

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
        if (!(o instanceof Dislike)) {
            return false;
        }
        Dislike dislike = (Dislike) o;
        return Objects.equals(actor, dislike.actor) && Objects.equals(object, dislike.object);
    }

    @Override
    public int hashCode() {
        return Objects.hash(actor, object);
    }

    @Override
    public String toString() {
        return "Dislike{" +
                "actor=" + actor +
                ", object=" + object +
                "}";
    }
}
