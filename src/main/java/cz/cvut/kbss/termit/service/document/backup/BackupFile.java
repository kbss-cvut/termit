package cz.cvut.kbss.termit.service.document.backup;


import java.io.File;
import java.time.Instant;

public record BackupFile(Instant timestamp, File file, BackupReason backupReason) {

}
