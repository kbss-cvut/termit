package cz.cvut.kbss.termit.model.changetracking;

import cz.cvut.kbss.jopa.model.annotations.OWLAnnotationProperty;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.util.Set;

// TODO: add entity class to IriMigrationChangeRecord
public class IdentifierChangeRecord extends AbstractChangeRecord {

    // TODO: entity class must be in the domain of has_new_value or the property must be changed
    @OWLAnnotationProperty(iri = Vocabulary.s_p_has_new_value)
    private Set<Object> newValue;

    public IdentifierChangeRecord() {
    }

    public Set<Object> getNewValue() {
        return newValue;
    }

    public void setNewValue(Set<Object> newValue) {
        this.newValue = newValue;
    }
}
