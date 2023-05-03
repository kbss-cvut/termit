package cz.cvut.kbss.termit.model.acl;

import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.model.UserRole;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.util.Objects;
import java.util.Optional;

@OWLClass(iri = Vocabulary.s_c_zaznam_rizeni_pristupu_uzivatelske_role)
public class RoleAccessControlRecord extends AccessControlRecord<UserRole> {

    public RoleAccessControlRecord() {
    }

    public RoleAccessControlRecord(AccessLevel accessLevel, UserRole holder) {
        super(accessLevel, holder);
    }

    @Override
    public Optional<AccessLevel> getAccessLevelFor(UserAccount user) {
        Objects.requireNonNull(user);
        assert getHolder() != null;
        return user.hasType(getHolder().getUri().toString()) ? Optional.of(getAccessLevel()) : Optional.empty();
    }
}
