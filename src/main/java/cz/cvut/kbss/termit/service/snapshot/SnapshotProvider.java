package cz.cvut.kbss.termit.service.snapshot;

import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.model.Asset;

import java.time.Instant;
import java.util.List;

/**
 * Interface for accessing asset snapshots.
 *
 * @param <T> Asset type
 */
public interface SnapshotProvider<T extends Asset<?>> {

    /**
     * Finds snapshots of the specified asset.
     * <p>
     * Note that the list does not contain the currently active version of the asset, as it is not considered a
     * snapshot.
     *
     * @param asset Asset whose snapshots to find
     * @return List of snapshots, sorted by date of creation (latest first)
     */
    List<Snapshot> findSnapshots(T asset);

    /**
     * Finds a version of the specified asset valid at the specified instant.
     * <p>
     * The result may be the current version, in case there is no snapshot matching the instant.
     *
     * @param asset Asset whose version to get
     * @param at    Instant at which the asset should be returned
     * @return Version of the asset valid at the specified instant
     */
    T findVersionValidAt(T asset, Instant at);
}
