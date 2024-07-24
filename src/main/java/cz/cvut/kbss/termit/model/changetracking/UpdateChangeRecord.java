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

import cz.cvut.kbss.jopa.model.annotations.OWLAnnotationProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.model.annotations.ParticipationConstraints;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.net.URI;
import java.util.Objects;
import java.util.Set;

@OWLClass(iri = Vocabulary.s_c_uprava_entity)
public class UpdateChangeRecord extends AbstractChangeRecord {

    @ParticipationConstraints(nonEmpty = true)
    @OWLObjectProperty(iri = Vocabulary.s_p_ma_zmeneny_atribut)
    private URI changedAttribute;

    @OWLAnnotationProperty(iri = Vocabulary.s_p_ma_puvodni_hodnotu)
    private Set<Object> originalValue;

    @OWLAnnotationProperty(iri = Vocabulary.s_p_ma_novou_hodnotu)
    private Set<Object> newValue;

    public UpdateChangeRecord() {
    }

    public UpdateChangeRecord(Asset<?> changedAsset) {
        super(changedAsset);
    }

    public URI getChangedAttribute() {
        return changedAttribute;
    }

    public void setChangedAttribute(URI changedAttribute) {
        this.changedAttribute = changedAttribute;
    }

    public Set<Object> getOriginalValue() {
        return originalValue;
    }

    public void setOriginalValue(Set<Object> originalValue) {
        this.originalValue = originalValue;
    }

    public Set<Object> getNewValue() {
        return newValue;
    }

    public void setNewValue(Set<Object> newValue) {
        this.newValue = newValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UpdateChangeRecord that)) {
            return false;
        }
        if (!super.equals(that)) {
            return false;
        }
        return changedAttribute.equals(that.changedAttribute);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), changedAttribute);
    }

    @Override
    public String toString() {
        return "UpdateChangeRecord{" +
                super.toString() +
                "changedAttribute=" + changedAttribute +
                "}";
    }
}
