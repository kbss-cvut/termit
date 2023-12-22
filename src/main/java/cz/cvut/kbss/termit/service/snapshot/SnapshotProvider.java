/*
 * TermIt
 * Copyright (C) 2023 Czech Technical University in Prague
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.cvut.kbss.termit.service.snapshot;

import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.exception.SnapshotNotEditableException;
import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.model.util.SupportsSnapshots;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

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
    Optional<T> findVersionValidAt(T asset, Instant at);

    /**
     * Checks that the specified asset being modified is not a snapshot.
     * <p>
     * If it is, a {@link SnapshotNotEditableException} is thrown.
     *
     * @param asset Asset to check for being a snapshot
     * @throws SnapshotNotEditableException If the specified asset is a snapshot
     */
    static void verifySnapshotNotModified(SupportsSnapshots asset) {
        if (asset.isSnapshot()) {
            throw SnapshotNotEditableException.create(asset);
        }
    }
}
