package cz.cvut.kbss.termit.exception;

import cz.cvut.kbss.termit.model.Asset;
import cz.cvut.kbss.termit.util.Utils;

/**
 * Indicates that an attempt to update a snapshot has been made.
 * <p>
 * Snapshot are, however, read-only and cannot be edited.
 */
public class SnapshotNotEditableException extends TermItException {

    public SnapshotNotEditableException(String message) {
        super(message);
    }

    public static SnapshotNotEditableException create(Asset<?> asset) {
        return new SnapshotNotEditableException(
                Utils.uriToString(asset.getUri()) + " is a snapshot and cannot be modified.");
    }
}
