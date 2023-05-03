package cz.cvut.kbss.termit.model.acl;

import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.model.UserGroup;
import cz.cvut.kbss.termit.util.Utils;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.util.Objects;
import java.util.Optional;

@OWLClass(iri = Vocabulary.s_c_zaznam_rizeni_pristupu_uzivatelske_skupiny)
public class UserGroupAccessControlRecord extends AccessControlRecord<UserGroup> {

    public UserGroupAccessControlRecord() {
    }

    public UserGroupAccessControlRecord(AccessLevel accessLevel, UserGroup holder) {
        super(accessLevel, holder);
    }

    @Override
    public Optional<AccessLevel> getAccessLevelFor(UserAccount user) {
        Objects.requireNonNull(user);
        assert getHolder() != null;

        return Utils.emptyIfNull(getHolder().getMembers()).stream().anyMatch(u -> u.getUri().equals(user.getUri())) ?
               Optional.of(getAccessLevel()) : Optional.empty();
    }
}
