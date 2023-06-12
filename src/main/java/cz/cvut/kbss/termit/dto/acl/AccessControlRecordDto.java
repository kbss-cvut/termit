package cz.cvut.kbss.termit.dto.acl;

import cz.cvut.kbss.jopa.model.annotations.*;
import cz.cvut.kbss.jopa.model.annotations.util.NonEntity;
import cz.cvut.kbss.termit.model.AbstractEntity;
import cz.cvut.kbss.termit.model.acl.AccessLevel;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.util.Set;

@OWLClass(iri = Vocabulary.s_c_zaznam_rizeni_pristupu)
@NonEntity
public class AccessControlRecordDto extends AbstractEntity {

    @Enumerated(EnumType.OBJECT_ONE_OF)
    @OWLObjectProperty(iri = Vocabulary.s_p_ma_uroven_pristupovych_opravneni)
    private AccessLevel accessLevel;

    @OWLObjectProperty(iri = Vocabulary.s_p_ma_drzitele_pristupovych_opravneni)
    private AccessHolderDto holder;

    @Types
    private Set<String> types;

    public AccessLevel getAccessLevel() {
        return accessLevel;
    }

    public void setAccessLevel(AccessLevel accessLevel) {
        this.accessLevel = accessLevel;
    }

    public AccessHolderDto getHolder() {
        return holder;
    }

    public void setHolder(AccessHolderDto holder) {
        this.holder = holder;
    }

    public Set<String> getTypes() {
        return types;
    }

    public void setTypes(Set<String> types) {
        this.types = types;
    }
}
