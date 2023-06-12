package cz.cvut.kbss.termit.model.changetracking;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import cz.cvut.kbss.jopa.model.annotations.*;
import cz.cvut.kbss.termit.model.AbstractEntity;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

/**
 * Represents a change to an asset.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "className")
@OWLClass(iri = Vocabulary.s_c_zmena)
public class AbstractChangeRecord extends AbstractEntity {

    @ParticipationConstraints(nonEmpty = true)
    @OWLDataProperty(iri = Vocabulary.s_p_ma_datum_a_cas_modifikace)
    private Instant timestamp;

    @ParticipationConstraints(nonEmpty = true)
    @OWLObjectProperty(iri = Vocabulary.s_p_ma_editora, fetch = FetchType.EAGER)
    private User author;

    @ParticipationConstraints(nonEmpty = true)
    @OWLObjectProperty(iri = Vocabulary.s_p_ma_zmenenou_entitu)
    private URI changedEntity;

    public AbstractChangeRecord() {
    }

    protected AbstractChangeRecord(Asset<?> changedEntity) {
        this.changedEntity = Objects.requireNonNull(changedEntity).getUri();
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public User getAuthor() {
        return author;
    }

    public void setAuthor(User author) {
        this.author = author;
    }

    public URI getChangedEntity() {
        return changedEntity;
    }

    public void setChangedEntity(URI changedEntity) {
        this.changedEntity = changedEntity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AbstractChangeRecord)) {
            return false;
        }
        AbstractChangeRecord that = (AbstractChangeRecord) o;
        return Objects.equals(timestamp, that.timestamp) &&
                Objects.equals(changedEntity, that.changedEntity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(timestamp, changedEntity);
    }

    @Override
    public String toString() {
        return "<" + getUri() + ">" +
                ", timestamp=" + timestamp +
                ", author=" + author +
                ", changedEntity=" + changedEntity;
    }
}
