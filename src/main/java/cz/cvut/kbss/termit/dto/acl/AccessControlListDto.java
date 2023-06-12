package cz.cvut.kbss.termit.dto.acl;

import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.model.annotations.util.NonEntity;
import cz.cvut.kbss.termit.model.AbstractEntity;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.util.Set;

@OWLClass(iri = Vocabulary.s_c_seznam_rizeni_pristupu)
@NonEntity
public class AccessControlListDto extends AbstractEntity {

    @OWLObjectProperty(iri = Vocabulary.s_p_ma_zaznam_rizeni_pristupu)
    private Set<AccessControlRecordDto> records;

    public Set<AccessControlRecordDto> getRecords() {
        return records;
    }

    public void setRecords(Set<AccessControlRecordDto> records) {
        this.records = records;
    }
}
