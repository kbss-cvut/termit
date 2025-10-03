package cz.cvut.kbss.termit.service.document.backup;


import java.io.File;
import java.time.Instant;

/**
 * Represents a backup {@link #file} created at {@link #timestamp}
 * with a {@link #backupReason}
 * <p>
 * Note that there are no guarantees about the format of the wrapped file or its existence.
 * @param timestamp the timestamp at which the backup was created
 * @param file the file containing the backup
 * @param backupReason the reason of backup creation
 */
public record BackupFile(Instant timestamp, File file, BackupReason backupReason) {

}
