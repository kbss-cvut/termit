package cz.cvut.kbss.termit.model.acl;

import com.fasterxml.jackson.annotation.JsonIgnore;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.termit.model.User;
import cz.cvut.kbss.termit.model.UserAccount;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.util.Objects;
import java.util.Optional;

@OWLClass(iri = Vocabulary.s_c_zaznam_rizeni_pristupu_uzivatele)
public class UserAccessControlRecord extends AccessControlRecord<User> {

    public UserAccessControlRecord() {
    }

    public UserAccessControlRecord(AccessLevel accessLevel, User holder) {
        super(accessLevel, holder);
    }

    @JsonIgnore
    @Override
    public Optional<AccessLevel> getAccessLevelFor(UserAccount user) {
        Objects.requireNonNull(user);
        assert getHolder() != null;

        return getHolder().getUri().equals(user.getUri()) ? Optional.of(getAccessLevel()) : Optional.empty();
    }
}
