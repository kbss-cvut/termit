package cz.cvut.kbss.termit.dto;

import cz.cvut.kbss.jopa.model.annotations.*;
import cz.cvut.kbss.termit.model.util.HasIdentifier;
import cz.cvut.kbss.termit.util.Vocabulary;

import java.io.Serializable;
import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.Set;

import static cz.cvut.kbss.termit.util.Utils.uriToString;

@SparqlResultSetMapping(name = "Snapshot",
                        classes = {@ConstructorResult(targetClass = Snapshot.class,
                                                      variables = {
                                                              @VariableResult(name = "s", type = URI.class),
                                                              @VariableResult(name = "created", type = Instant.class),
                                                              @VariableResult(name = "asset", type = URI.class),
                                                              @VariableResult(name = "type", type = String.class)
                                                      })})
@OWLClass(iri = Vocabulary.s_c_verze_objektu)
public class Snapshot implements HasIdentifier, Serializable {

    @Id
    private URI uri;

    @OWLDataProperty(iri = Vocabulary.s_p_ma_datum_a_cas_vytvoreni_verze)
    private Instant created;

    @OWLObjectProperty(iri = Vocabulary.s_p_je_verzi)
    private URI versionOf;

    @Types
    private Set<String> types;

    public Snapshot() {
    }

    public Snapshot(URI uri, Instant created, URI asset, String type) {
        this.uri = uri;
        this.created = created;
        this.versionOf = asset;
        this.types = Collections.singleton(type);
    }

    @Override
    public URI getUri() {
        return uri;
    }

    @Override
    public void setUri(URI uri) {
        this.uri = uri;
    }

    public Instant getCreated() {
        return created;
    }

    public void setCreated(Instant created) {
        this.created = created;
    }

    public URI getVersionOf() {
        return versionOf;
    }

    public void setVersionOf(URI versionOf) {
        this.versionOf = versionOf;
    }

    public Set<String> getTypes() {
        return types;
    }

    public void setTypes(Set<String> types) {
        this.types = types;
    }

    @Override
    public String toString() {
        return "Snapshot{" +
                uriToString(uri) +
                ", created=" + created +
                ", versionOf=" + uriToString(versionOf) +
                '}';
    }
}
