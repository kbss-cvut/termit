package cz.cvut.kbss.termit.service.document.backup;

public enum BackupReason {
    TEXT_ANALYSIS,
    NEW_OCCURRENCE,
    SCHEDULED,
    BACKUP_RESTORE,
    UNKNOWN;

    public static BackupReason from(String name) {
        for (BackupReason value : BackupReason.values()) {
            if (value.name().equalsIgnoreCase(name)) {
                return value;
            }
        }
        return BackupReason.UNKNOWN;
    }
}
