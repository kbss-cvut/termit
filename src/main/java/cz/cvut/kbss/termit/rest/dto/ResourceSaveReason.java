package cz.cvut.kbss.termit.rest.dto;

import cz.cvut.kbss.termit.service.document.backup.BackupReason;

import java.util.Optional;

/**
 * Reason for saving a resource
 */
public enum ResourceSaveReason {
    NEW_OCCURRENCE(BackupReason.NEW_OCCURRENCE),
    REMOVE_OCCURRENCE(BackupReason.REMOVE_OCCURRENCE),
    OCCURRENCE_STATE_CHANGE,
    CREATE_FILE,
    REUPLOAD(BackupReason.REUPLOAD),
    UNKNOWN;

    private final BackupReason backupReason;

    ResourceSaveReason(BackupReason backupReason) {
        this.backupReason = backupReason;
    }

    ResourceSaveReason() {
        this.backupReason = null;
    }

    public Optional<BackupReason> getBackupReason() {
        return Optional.ofNullable(backupReason);
    }
}
