package cz.cvut.kbss.termit.model.acl;

import cz.cvut.kbss.jopa.model.annotations.FetchType;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.termit.model.UserGroup;
import cz.cvut.kbss.termit.util.Vocabulary;

@OWLClass(iri = Vocabulary.s_c_zaznam_rizeni_pristupu_uzivatelske_skupiny)
public class UserGroupAccessControlRecord extends AccessControlRecord {

    @OWLObjectProperty(iri = Vocabulary.s_p_ma_drzitele_pristupovych_opravneni, fetch = FetchType.EAGER)
    private UserGroup holder;

    public UserGroup getHolder() {
        return holder;
    }

    public void setHolder(UserGroup holder) {
        this.holder = holder;
    }

    @Override
    public String toString() {
        return "UserGroupAccessControlRecord{" +
                holder + " -> " + getAccessLevel() +
                '}';
    }
}
