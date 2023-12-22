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
package cz.cvut.kbss.termit.persistence.snapshot;

import cz.cvut.kbss.termit.dto.Snapshot;
import cz.cvut.kbss.termit.model.Vocabulary;
import cz.cvut.kbss.termit.util.Configuration;
import cz.cvut.kbss.termit.util.Constants;

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
        final String strTimestamp = Constants.TIMESTAMP_FORMATTER.format(timestamp);
        return snapshotSeparator.getSeparator() + "/" + strTimestamp;
    }
}
