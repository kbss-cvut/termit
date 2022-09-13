package cz.cvut.kbss.termit.persistence.snapshot;

import cz.cvut.kbss.termit.dto.Snapshot;

/**
 * Allows removal of asset snapshots.
 */
public interface SnapshotRemover {

    /**
     * Deletes the specified snapshot.
     *
     * @param snapshot Snapshot to remove
     */
    void removeSnapshot(Snapshot snapshot);
}
