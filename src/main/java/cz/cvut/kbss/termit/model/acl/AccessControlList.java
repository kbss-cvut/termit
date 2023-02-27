package cz.cvut.kbss.termit.model.acl;

import cz.cvut.kbss.jopa.model.annotations.CascadeType;
import cz.cvut.kbss.jopa.model.annotations.FetchType;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.termit.model.AbstractEntity;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Instances of this class specify the levels of access various
 */
@OWLClass(iri = Vocabulary.s_c_seznam_rizeni_pristupu)
public class AccessControlList extends AbstractEntity {

    @OWLObjectProperty(iri = Vocabulary.s_p_ma_zaznam_rizeni_pristupu, fetch = FetchType.EAGER,
                       cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private Set<AccessControlRecord> records;

    public Set<AccessControlRecord> getRecords() {
        return records;
    }

    public void setRecords(Set<AccessControlRecord> records) {
        this.records = records;
    }

    public void addRecord(AccessControlRecord record) {
        Objects.requireNonNull(record);
        if (records == null) {
            this.records = new HashSet<>();
        }
        records.add(record);
    }

    @Override
    public String toString() {
        return "AccessControlList{" +
                "records=" + records +
                '}';
    }
}
