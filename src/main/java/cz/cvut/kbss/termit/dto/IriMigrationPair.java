package cz.cvut.kbss.termit.dto;

import cz.cvut.kbss.termit.exception.InvalidParameterException;
import cz.cvut.kbss.termit.util.Utils;

import java.net.URI;
import java.util.Objects;

/**
 * A pair of IRIs used for migration from the {@code originalIri} to the {@code newIri}.
 *
 * @param originalIri the original IRI which should be replaced with {@code newIri}
 * @param newIri the new IRI that should replace the {@code originalIri}
 */
public record IriMigrationPair(URI originalIri, URI newIri) {
    public IriMigrationPair {
        Objects.requireNonNull(originalIri);
        Objects.requireNonNull(newIri);
        if (originalIri.equals(newIri)) {
            throw new InvalidParameterException("Cannot migrate to the same IRI");
        }
    }

    @Override
    public String toString() {
        return "IRI Migration " + Utils.uriToString(originalIri) + " -> " + Utils.uriToString(newIri);
    }
}
