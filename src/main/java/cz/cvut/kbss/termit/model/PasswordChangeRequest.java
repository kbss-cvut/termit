package cz.cvut.kbss.termit.model;

import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLDataProperty;
import cz.cvut.kbss.jopa.model.annotations.OWLObjectProperty;
import cz.cvut.kbss.jopa.model.annotations.ParticipationConstraints;
import cz.cvut.kbss.jopa.vocabulary.DC;
import cz.cvut.kbss.termit.util.Vocabulary;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

@OWLClass(iri = Vocabulary.s_c_pozadavek_na_zmenu_hesla)
public class PasswordChangeRequest extends AbstractEntity {
    /**
     * Associated user account.
     */
    @NotNull
    @ParticipationConstraints(nonEmpty = true)
    @OWLObjectProperty(iri = DC.Terms.SUBJECT)
    private UserAccount userAccount;

    /**
     * Token value.
     */
    @NotNull
    @ParticipationConstraints(nonEmpty = true)
    @OWLDataProperty(iri = DC.Terms.IDENTIFIER, simpleLiteral = true)
    private String token;

    /**
     * Token creation timestamp.
     */
    @NotNull
    @ParticipationConstraints(nonEmpty = true)
    @OWLDataProperty(iri = DC.Terms.CREATED)
    private Instant createdAt;

    public UserAccount getUserAccount() {
        return userAccount;
    }

    public void setUserAccount(UserAccount userAccount) {
        this.userAccount = userAccount;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
