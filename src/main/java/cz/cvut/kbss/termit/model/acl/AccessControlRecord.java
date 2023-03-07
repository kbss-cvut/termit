package cz.cvut.kbss.termit.model.acl;

import cz.cvut.kbss.jopa.model.annotations.*;
import cz.cvut.kbss.termit.model.AbstractEntity;
import cz.cvut.kbss.termit.model.AccessControlAgent;
import cz.cvut.kbss.termit.util.Vocabulary;

@OWLClass(iri = Vocabulary.s_c_zaznam_rizeni_pristupu)
public abstract class AccessControlRecord<T extends AccessControlAgent> extends AbstractEntity {

    @Enumerated(EnumType.OBJECT_ONE_OF)
    @OWLObjectProperty(iri = Vocabulary.s_p_ma_uroven_pristupovych_opravneni)
    private AccessLevel accessLevel;

    @OWLObjectProperty(iri = Vocabulary.s_p_ma_drzitele_pristupovych_opravneni, fetch = FetchType.EAGER)
    private T holder;

    public AccessLevel getAccessLevel() {
        return accessLevel;
    }

    public void setAccessLevel(AccessLevel accessLevel) {
        this.accessLevel = accessLevel;
    }

    public T getHolder() {
        return holder;
    }

    public void setHolder(T holder) {
        this.holder = holder;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                holder + " -> " + getAccessLevel() +
                '}';
    }
}
