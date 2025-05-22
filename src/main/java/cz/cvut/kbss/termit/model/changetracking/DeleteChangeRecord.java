/*
 * TermIt
 * Copyright (C) 2025 Czech Technical University in Prague
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

import cz.cvut.kbss.jopa.model.MultilingualString;
import cz.cvut.kbss.jopa.model.annotations.OWLAnnotationProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.ParticipationConstraints;
import cz.cvut.kbss.jopa.vocabulary.RDFS;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.util.Vocabulary;
import jakarta.annotation.Nonnull;

import java.util.Objects;

/**
 * Represents a record of asset deletion.
 */
@OWLClass(iri = Vocabulary.s_c_smazani_entity)
public class DeleteChangeRecord extends AbstractChangeRecord {
    @ParticipationConstraints(nonEmpty = true)
    @OWLAnnotationProperty(iri = RDFS.LABEL)
    private MultilingualString label;

    /**
     * Creates a new instance.
     * @param changedEntity the changed asset
     * @throws IllegalArgumentException If the label type is not String or MultilingualString
     */
    public DeleteChangeRecord(Asset<?> changedEntity) {
        super(changedEntity);

        if (changedEntity.getLabel() instanceof String stringLabel) {
            this.label = MultilingualString.create(stringLabel, null);
        } else if (changedEntity.getLabel() instanceof MultilingualString multilingualLabel) {
            this.label = multilingualLabel;
        } else {
            throw new IllegalArgumentException("Unsupported label type: " + changedEntity.getLabel().getClass());
        }
    }

    public DeleteChangeRecord() {
        super();
    }

    public MultilingualString getLabel() {
        return label;
    }

    public void setLabel(MultilingualString label) {
        this.label = label;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DeleteChangeRecord that)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        return Objects.equals(label, that.label);
    }

    @Override
    public String toString() {
        return "DeleteChangeRecord{" +
                super.toString() +
                ", label=" + label +
                '}';
    }

    @Override
    public int compareTo(@Nonnull AbstractChangeRecord o) {
        if (o instanceof UpdateChangeRecord) {
            return 1;
        }
        if (o instanceof PersistChangeRecord) {
            return 1;
        }
        return super.compareTo(o);
    }
}
