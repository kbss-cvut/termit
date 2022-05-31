package cz.cvut.kbss.termit.service.snapshot;

import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.util.Configuration;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Allows creating snapshots of assets.
 * <p>
 * Snapshots are read-only versions of assets used to track history of complete state of assets. In comparison to change
 * records, snapshots may be used to mark important milestones in the lifecycle of assets (e.g., vocabulary version
 * being published).
 *
 * @param <T> Asset type
 */
public abstract class SnapshotCreator<T extends Asset<?>> {

    private final IdentifierResolver identifierResolver;

    private final Configuration.Namespace.NamespaceDetail snapshotSeparator;

    private Instant timestamp;

    protected SnapshotCreator(IdentifierResolver identifierResolver, Configuration configuration) {
        this.identifierResolver = identifierResolver;
        this.snapshotSeparator = configuration.getNamespace().getSnapshot();
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = Objects.requireNonNull(timestamp);
    }

    /**
     * Creates a snapshot of the specified asset.
     *
     * @param asset Asset to snapshot
     * @return The created snapshot representation
     */
    public abstract T createSnapshot(T asset);

    protected URI generateSnapshotIdentifier(T asset) {
        final String strTimestamp = timestamp.truncatedTo(ChronoUnit.SECONDS)
                                             .toString().replace(":", "")
                                             .replace(" ", "");
        return identifierResolver.generateDerivedIdentifier(asset.getUri(), snapshotSeparator.getSeparator(),
                                                            strTimestamp);
    }
}
