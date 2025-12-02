package cz.cvut.kbss.termit.service.document.backup;

import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.persistence.dao.ResourceDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Periodically creates backups for document files
 */
@Service
public class ScheduledDocumentBackupManager {
    private static final Logger LOG = LoggerFactory.getLogger(ScheduledDocumentBackupManager.class);
    private final ResourceDao resourceDao;
    private final DocumentBackupManager backupManager;

    public ScheduledDocumentBackupManager(ResourceDao resourceDao, DocumentBackupManager backupManager) {
        this.resourceDao = resourceDao;
        this.backupManager = backupManager;
    }

    /**
     * Creates a scheduled backup of all document files with missing backup since last modification.
     * <p>
     * Every day at {@code 03:00}.
     */
    @Scheduled(cron = "0 0 3 * * *")
    void backupModifiedDocuments() {
        List<File> toBackup = resourceDao.findModifiedFilesAfterLastBackup();
        if (!toBackup.isEmpty()) {
            LOG.info("Starting scheduled backup for {} document files", toBackup.size());
        }
        for (File file : toBackup) {
            try {
                backupManager.createBackup(file, BackupReason.SCHEDULED);
            } catch (Exception e) {
                LOG.error("Failed to create scheduled backup for file <{}>", file.getUri(), e);
            }
        }
    }

}
