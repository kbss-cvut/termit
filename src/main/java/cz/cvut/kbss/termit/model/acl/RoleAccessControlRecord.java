package cz.cvut.kbss.termit.model.acl;

import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.termit.model.UserRole;
import cz.cvut.kbss.termit.util.Vocabulary;

@OWLClass(iri = Vocabulary.s_c_zaznam_rizeni_pristupu_uzivatelske_role)
public class RoleAccessControlRecord extends AccessControlRecord<UserRole> {

    public RoleAccessControlRecord() {
    }

    public RoleAccessControlRecord(AccessLevel accessLevel, UserRole holder) {
        super(accessLevel, holder);
    }
}
