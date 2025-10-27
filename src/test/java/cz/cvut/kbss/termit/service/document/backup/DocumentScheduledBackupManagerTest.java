package cz.cvut.kbss.termit.service.document.backup;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.service.document.BaseDocumentTestRunner;
import cz.cvut.kbss.termit.util.Utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class DocumentScheduledBackupManagerTest extends BaseDocumentTestRunner {
    @Autowired
    private DocumentScheduledBackupManager sut;

    @Autowired
    private EntityManager em;

    @MockitoBean
    private DocumentBackupManager backupManager;

    private List<File> backupAfterModified;
    private List<File> modifiedAfterBackup;

    private Instant now;
    @BeforeEach
    public void generateFiles() {
        now = Utils.timestamp();

        backupAfterModified = new ArrayList<>();
        modifiedAfterBackup = new ArrayList<>();

        transactional(() -> em.persist(document));

        // generate files with different modified timestamps
        Map.of(now.minus(25, ChronoUnit.HOURS), now.minus(24, ChronoUnit.HOURS),
                now.minus(23, ChronoUnit.HOURS), now.minus(25, ChronoUnit.HOURS),
                now.minusSeconds(10), now.minus(1, ChronoUnit.MINUTES),
                Instant.EPOCH, now.minus(5, ChronoUnit.DAYS))
           .forEach((modified, lastBackup) -> {

            File file = generateDocumentFile();
            file.setModified(modified);
            file.setLastBackup(lastBackup);
            transactional(() -> em.persist(file));

            if (modified.isAfter(lastBackup)) {
                modifiedAfterBackup.add(file);
            } else {
                backupAfterModified.add(file);
            }
        });
    }

    private File generateDocumentFile() {
        java.io.File physicalFile = generateFile();
        File file = new File();
        file.setLabel(physicalFile.getName());
        document.addFile(file);
        file.setDocument(document);
        file.setUri(Generator.generateUri());
        return file;
    }

    @Test
    void findFilesToBackupReturnsFilesModifiedAfterLastBackup() {
        List<File> files = sut.findFilesToBackup();
        assertEquals(modifiedAfterBackup.size(), files.size());
        assertTrue(files.containsAll(modifiedAfterBackup));
    }

    @Test
    void backupModifiedDocumentsBackupsDocumentsModifiedInLast24Hours() {
        doNothing().when(backupManager).createBackup(any(), any());
        sut.backupModifiedDocuments();
        ArgumentCaptor<File> fileCaptor = ArgumentCaptor.forClass(File.class);
        verify(backupManager, times(modifiedAfterBackup.size())).createBackup(fileCaptor.capture(), eq(BackupReason.SCHEDULED));
        List<File> files = fileCaptor.getAllValues();
        assertEquals(modifiedAfterBackup.size(), files.size());
        assertTrue(files.containsAll(modifiedAfterBackup));
    }
}
