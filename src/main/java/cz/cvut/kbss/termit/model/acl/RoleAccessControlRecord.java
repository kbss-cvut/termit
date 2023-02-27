package cz.cvut.kbss.termit.model.acl;

import cz.cvut.kbss.jopa.model.annotations.FetchType;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.termit.model.UserRole;
import cz.cvut.kbss.termit.util.Vocabulary;

@OWLClass(iri = Vocabulary.s_c_zaznam_rizeni_pristupu_uzivatelske_role)
public class RoleAccessControlRecord extends AccessControlRecord {

    @OWLObjectProperty(iri = Vocabulary.s_p_ma_drzitele_pristupovych_opravneni, fetch = FetchType.EAGER)
    private UserRole holder;

    public UserRole getHolder() {
        return holder;
    }

    public void setHolder(UserRole holder) {
        this.holder = holder;
    }

    @Override
    public String toString() {
        return "RoleAccessControlRecord{" +
                holder + " -> " + getAccessLevel() +
                '}';
    }
}
