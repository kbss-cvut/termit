package cz.cvut.kbss.termit.service.document.backup;

import cz.cvut.kbss.jopa.model.EntityManager;
import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.service.document.BaseDocumentTestRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class ScheduledDocumentBackupManagerTest extends BaseDocumentTestRunner {
    @Autowired
    private ScheduledDocumentBackupManager sut;

    @Autowired
    private EntityManager em;

    @MockitoBean
    private DocumentBackupManager backupManager;

    private List<File> modifiedAfterBackup;

    @BeforeEach
    public void generateFiles() {
        modifiedAfterBackup = new ArrayList<>();

        transactional(() -> em.persist(document));

        // generate files with different modified timestamps
        Generator.generateDocumentFilesWithBackupTimestamps(document)
                .forEach(file -> {
                    transactional(() -> em.persist(file));
                    if (file.getModified().isAfter(file.getLastBackup())) {
                        modifiedAfterBackup.add(file);
                    }
                });
    }

    @Test
    void backupModifiedDocumentsBackupsDocumentsModifiedInLast24Hours() {
        doNothing().when(backupManager).createBackup(any(), any());
        sut.backupModifiedDocuments();
        ArgumentCaptor<File> fileCaptor = ArgumentCaptor.forClass(File.class);
        verify(backupManager, times(modifiedAfterBackup.size())).createBackup(fileCaptor.capture(), eq(BackupReason.SCHEDULED));
        List<File> files = fileCaptor.getAllValues();
        assertFalse(files.isEmpty());
        assertEquals(modifiedAfterBackup.size(), files.size());
        assertTrue(files.containsAll(modifiedAfterBackup));
    }
}
