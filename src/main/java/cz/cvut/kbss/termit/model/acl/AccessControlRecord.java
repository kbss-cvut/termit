package cz.cvut.kbss.termit.model.acl;

import cz.cvut.kbss.jopa.model.annotations.*;
import cz.cvut.kbss.termit.model.AbstractEntity;
import cz.cvut.kbss.termit.util.Vocabulary;

@OWLClass(iri = Vocabulary.s_c_zaznam_rizeni_pristupu)
public abstract class AccessControlRecord extends AbstractEntity {

    @Enumerated(EnumType.OBJECT_ONE_OF)
    @OWLObjectProperty(iri = Vocabulary.s_p_ma_uroven_pristupovych_opravneni)
    private AccessLevel accessLevel;

    public AccessLevel getAccessLevel() {
        return accessLevel;
    }

    public void setAccessLevel(AccessLevel accessLevel) {
        this.accessLevel = accessLevel;
    }
}
