package cz.cvut.kbss.termit.dto;

import cz.cvut.kbss.jopa.model.annotations.Id;
import cz.cvut.kbss.jopa.model.annotations.OWLClass;
import cz.cvut.kbss.jopa.model.annotations.OWLDataProperty;
import cz.cvut.kbss.jopa.model.annotations.util.NonEntity;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;

@NonEntity
@OWLClass(iri = Vocabulary.s_c_osobni_pristupovy_token)
public class PersonalAccessTokenDto implements HasIdentifier {
    @Id
    private URI uri;

    @OWLDataProperty(iri = Vocabulary.s_p_ma_datum_a_cas_vytvoreni)
    private Instant created;

    @OWLDataProperty(iri = Vocabulary.s_p_ma_datum_expirace)
    private LocalDate expirationDate;

    @OWLDataProperty(iri = Vocabulary.s_p_sioc_last_activity_date)
    private Instant lastUsed;

    @Override
    public URI getUri() {
        return uri;
    }

    @Override
    public void setUri(URI uri) {
        this.uri = uri;
    }

    public LocalDate getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(LocalDate expirationDate) {
        this.expirationDate = expirationDate;
    }

    public Instant getLastUsed() {
        return lastUsed;
    }

    public void setLastUsed(Instant lastUsed) {
        this.lastUsed = lastUsed;
    }

    public Instant getCreated() {
        return created;
    }

    public void setCreated(Instant created) {
        this.created = created;
    }
}
