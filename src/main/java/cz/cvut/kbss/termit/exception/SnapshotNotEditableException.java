package cz.cvut.kbss.termit.exception;

import cz.cvut.kbss.termit.model.util.HasIdentifier;

import static cz.cvut.kbss.termit.util.Utils.uriToString;

/**
 * Indicates that an attempt to update a snapshot has been made.
 * <p>
 * Snapshot are, however, read-only and cannot be edited.
 */
public class SnapshotNotEditableException extends TermItException {

    public SnapshotNotEditableException(String message) {
        super(message);
    }

    public static SnapshotNotEditableException create(HasIdentifier asset) {
        return new SnapshotNotEditableException(uriToString(asset.getUri()) + " is a snapshot and cannot be modified.");
    }
}
