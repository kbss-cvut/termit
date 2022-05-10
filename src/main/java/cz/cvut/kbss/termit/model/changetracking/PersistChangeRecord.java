package cz.cvut.kbss.termit.model.changetracking;

import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.util.Vocabulary;

@OWLClass(iri = Vocabulary.s_c_vytvoreni_entity)
public class PersistChangeRecord extends AbstractChangeRecord {

    public PersistChangeRecord() {
    }

    public PersistChangeRecord(Asset<?> changedAsset) {
        super(changedAsset);
    }

    @Override
    public String toString() {
        return "PersistChangeRecord{" + super.toString() + '}';
    }
}
