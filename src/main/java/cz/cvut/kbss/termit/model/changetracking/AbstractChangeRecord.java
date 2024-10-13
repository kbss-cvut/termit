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
package cz.cvut.kbss.termit.model.changetracking;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import cz.cvut.kbss.jopa.model.annotations.FetchType;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLDataProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.model.annotations.ParticipationConstraints;
import cz.cvut.kbss.termit.model.AbstractEntity;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.util.Vocabulary;
import jakarta.annotation.Nonnull;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

/**
 * Represents a change to an asset.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "className")
@OWLClass(iri = Vocabulary.s_c_zmena)
public class AbstractChangeRecord extends AbstractEntity implements Comparable<AbstractChangeRecord> {

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
        if (!(o instanceof AbstractChangeRecord that)) {
            return false;
        }
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

    @Override
    public int compareTo(@Nonnull AbstractChangeRecord o) {
        final int timestampDiff = getTimestamp().compareTo(o.getTimestamp());
        if (timestampDiff != 0) {
            return timestampDiff;
        }

        return getUri().compareTo(o.getUri());
    }
}
