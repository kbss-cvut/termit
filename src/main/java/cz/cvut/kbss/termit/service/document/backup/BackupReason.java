package cz.cvut.kbss.termit.service.document.backup;

/**
 * The reason for creation of a document file backup
 */
public enum BackupReason {
    REUPLOAD,
    TEXT_ANALYSIS,
    NEW_OCCURRENCE,
    REMOVE_OCCURRENCE,
    SCHEDULED,
    BACKUP_RESTORE,
    UNKNOWN;

    /**
     * Matches the {@code name} with a {@link BackupReason} while ignoring case.
     *
     * @param name the name to match
     * @return the matched {@link BackupReason} or {@link #UNKNOWN}
     */
    public static BackupReason from(String name) {
        for (BackupReason value : BackupReason.values()) {
            if (value.name().equalsIgnoreCase(name)) {
                return value;
            }
        }
        return BackupReason.UNKNOWN;
    }
}
