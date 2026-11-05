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

import cz.cvut.kbss.jopa.model.annotations.OWLAnnotationProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.util.Set;

@OWLClass(iri = Vocabulary.s_c_update_of_entity)
public class UpdateChangeRecord extends AttributeChangeRecord<Set<Object>> {

    @OWLAnnotationProperty(iri = Vocabulary.s_p_has_original_value)
    private Set<Object> originalValue;

    @OWLAnnotationProperty(iri = Vocabulary.s_p_has_new_value)
    private Set<Object> newValue;

    public UpdateChangeRecord() {}

    public UpdateChangeRecord(Asset<?> changedAsset) {
        super(changedAsset);
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
        return super.equals(o);
    }

    @Override
    public String toString() {
        return "UpdateChangeRecord{" + super.toString() + "changedAttribute=" + getChangedAttribute() + "}";
    }
}
