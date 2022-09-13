package cz.cvut.kbss.termit.service.business;

import cz.cvut.kbss.termit.dto.Snapshot;

import java.net.URI;

/**
 * Manages asset snapshots.
 */
public interface SnapshotService {

    /**
     * Finds a snapshot with the specified identifier.
     *
     * @param id Snapshot identifier
     * @return Matching snapshot
     * @throws cz.cvut.kbss.termit.exception.NotFoundException When no matching item is found
     */
    Snapshot findRequired(URI id);

    /**
     * Removes the specified snapshot.
     *
     * @param snapshot Snapshot to remove
     */
    void remove(Snapshot snapshot);
}
