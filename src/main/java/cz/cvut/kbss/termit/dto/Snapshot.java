package cz.cvut.kbss.termit.dto;

import cz.cvut.kbss.jopa.model.annotations.Id;
import cz.cvut.kbss.jopa.model.annotations.Types;
import cz.cvut.kbss.termit.model.util.HasIdentifier;

import java.io.Serializable;
import java.net.URI;
import java.time.Instant;
import java.util.Set;

import static cz.cvut.kbss.termit.util.Utils.uriToString;

// TODO Ontology mapping
public class Snapshot implements HasIdentifier, Serializable {

    @Id
    private URI uri;

    private Instant created;

    private URI versionOf;

    @Types
    private Set<String> types;

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
