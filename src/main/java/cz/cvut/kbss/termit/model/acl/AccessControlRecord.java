package cz.cvut.kbss.termit.model.acl;

import cz.cvut.kbss.jopa.model.annotations.*;
import cz.cvut.kbss.termit.model.AbstractEntity;
import cz.cvut.kbss.termit.model.AccessControlAgent;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.util.Objects;

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
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AccessControlRecord)) {
            return false;
        }
        AccessControlRecord<?> that = (AccessControlRecord<?>) o;
        return accessLevel == that.accessLevel && Objects.equals(holder, that.holder);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accessLevel, holder);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                holder + " -> " + getAccessLevel() +
                '}';
    }
}
