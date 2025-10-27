package cz.cvut.kbss.termit.service.document.backup;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class DocumentScheduledBackupManager {
    private static final Logger LOG = LoggerFactory.getLogger(DocumentScheduledBackupManager.class);
    private final EntityManager em;
    private final DocumentBackupManager backupManager;

    public DocumentScheduledBackupManager(EntityManager em, DocumentBackupManager backupManager) {
        this.em = em;
        this.backupManager = backupManager;
    }

    /**
     * Creates a scheduled backup of all document files modified in last 24 hours.
     * <p>
     * Every day at {@code 03:00}.
     */
    @Scheduled(cron = "0 0 3 * * *")
    void backupModifiedDocuments() {
        List<File> toBackup = findFilesModifiedSince(Utils.timestamp().minus(24, ChronoUnit.HOURS));
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

    List<File> findFilesModifiedSince(Instant since) {
        return em.createQuery("SELECT DISTINCT f FROM File f WHERE f.modified > :since", File.class)
                .setParameter("since", since)
                .getResultList();
    }

}
