package cz.cvut.kbss.termit.persistence.snapshot;

import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.util.Configuration;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Creates snapshot of a vocabulary and its contents.
 * <p>
 * Implementations may cascade the snapshot to other vocabularies.
 * <p>
 * A new instance should be created every time snapshot operation is triggered by the client.
 */
public abstract class SnapshotCreator {

    protected final Instant timestamp = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    private final Configuration.Namespace.NamespaceDetail snapshotSeparator;

    protected SnapshotCreator(Configuration configuration) {
        this.snapshotSeparator = configuration.getNamespace().getSnapshot();
    }

    /**
     * Creates a snapshot of the specified vocabulary and returns it.
     *
     * @param vocabulary Vocabulary to snapshot
     * @return Snapshot metadata
     */
    public abstract Snapshot createSnapshot(Vocabulary vocabulary);

    /**
     * Gets a timestamp-based suffix for snapshot identifiers.
     * <p>
     * This suffix is added to the identifier of the asset being versioned.
     *
     * @return Snapshot suffix
     */
    protected String getSnapshotSuffix() {
        final String strTimestamp = timestamp.truncatedTo(ChronoUnit.SECONDS)
                                             .toString().replace(":", "")
                                             .replace(" ", "");
        return snapshotSeparator.getSeparator() + "/" + strTimestamp;
    }
}
