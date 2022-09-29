package cz.cvut.kbss.termit.dto;

import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLDataProperty;
import cz.cvut.kbss.termit.model.AbstractUser;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.time.Instant;

@OWLClass(iri = Vocabulary.s_c_uzivatel_termitu)
public class CurrentUserDto extends AbstractUser {

    @OWLDataProperty(iri = Vocabulary.s_p_last_activity_date)
    private Instant lastSeen;

    public CurrentUserDto() {
    }

    public CurrentUserDto(AbstractUser user) {
        user.copyAttributes(this);
    }

    public Instant getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(Instant lastSeen) {
        this.lastSeen = lastSeen;
    }
}
