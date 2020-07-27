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

    public UpdateChangeRecord(Asset changedAsset) {
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
        if (!(o instanceof UpdateChangeRecord)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        UpdateChangeRecord that = (UpdateChangeRecord) o;
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
