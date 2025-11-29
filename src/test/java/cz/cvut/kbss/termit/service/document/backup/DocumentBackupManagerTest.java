package cz.cvut.kbss.termit.service.document.backup;

import cz.cvut.kbss.termit.environment.Generator;
import cz.cvut.kbss.termit.exception.NotFoundException;
import cz.cvut.kbss.termit.model.resource.File;
import cz.cvut.kbss.termit.service.IdentifierResolver;
import cz.cvut.kbss.termit.service.document.BaseDocumentTestRunner;
import cz.cvut.kbss.termit.service.repository.ResourceRepositoryService;
import cz.cvut.kbss.termit.util.Utils;
import org.apache.commons.compress.compressors.bzip2.BZip2Utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

public class DocumentBackupManagerTest extends BaseDocumentTestRunner {
    private static final String BACKUP_REASON_VALUES_METHOD_SIGNATURE = "cz.cvut.kbss.termit.service.document.backup.BackupReason#values()";
    private DocumentBackupManager sut;

    @MockitoBean
    ResourceRepositoryService resourceRepositoryService;

    @BeforeEach
    void setupSut() {
        sut = new DocumentBackupManager(configuration, resourceRepositoryService);
    }

    @Test
    void createBackupUpdatesFileLastBackupTimestamp() {
        Instant old = Instant.now().minusSeconds(10);
        final File file = new File();
        file.setModified(old);
        file.setLastBackup(old);
        final java.io.File physicalFile = generateFile();
        file.setLabel(physicalFile.getName());
        document.addFile(file);
        file.setDocument(document);
        sut.createBackup(file, BackupReason.UNKNOWN);
        verify(resourceRepositoryService).update(eq(file));
        assertEquals(old, file.getModified()); // no change expected
        assertTrue(old.isBefore(file.getLastBackup()));
    }

    @Test
    void createBackupCreatesBackupOfFileWithoutExtension() throws Exception {
        final File file = new File();
        final java.io.File physicalFile = generateFile();
        final java.io.File docDir = physicalFile.getParentFile();
        final java.io.File withoutExtension = new java.io.File(
                docDir.getAbsolutePath() + java.io.File.separator + "withoutExtension");
        withoutExtension.deleteOnExit();
        Files.copy(physicalFile.toPath(), withoutExtension.toPath());
        file.setLabel(withoutExtension.getName());
        document.addFile(file);
        file.setDocument(document);
        assertNotNull(docDir.listFiles());
        sut.createBackup(file, BackupReason.UNKNOWN);
        final java.io.File[] files = docDir.listFiles((d, name) -> name.startsWith("withoutExtension"));
        assertNotNull(files);
        assertEquals(2, files.length);
    }

    @Test
    void createBackupSanitizesFileLabelToEnsureValidFileName() throws Exception {
        final File file = new File();
        file.setUri(Generator.generateUri());
        final String label = "Zákon 130/2002";
        file.setLabel(label);
        document.addFile(file);
        file.setDocument(document);
        final java.io.File content = new java.io.File(documentDir.toFile(), IdentifierResolver.sanitizeFileName(label));
        content.deleteOnExit();
        Files.write(content.toPath(), CONTENT.getBytes());
        sut.createBackup(file, BackupReason.UNKNOWN);

        final java.io.File[] files = documentDir.toFile().listFiles();
        assertNotNull(files);
        assertEquals(2, files.length);
        for (java.io.File f : files) {
            try {
                assertThat(f.getName(), startsWith(IdentifierResolver.sanitizeFileName(label)));
            } finally {
                f.deleteOnExit();
            }
        }
    }

    @Test
    void createBackupCreatesBackupWithBzip2Extension() throws Exception {
        final File file = new File();
        final java.io.File physicalFile = generateFile();
        file.setLabel(physicalFile.getName());
        document.addFile(file);
        file.setDocument(document);
        final java.io.File docDir = physicalFile.getParentFile();
        assertNotNull(docDir.listFiles());
        assertEquals(1, docDir.listFiles().length);
        sut.createBackup(file, BackupReason.UNKNOWN);
        assertEquals(2, docDir.listFiles().length);
        // check that there is a backup with a name ending with the given reason
        String backupName = Arrays.stream(docDir.listFiles())
              .map(java.io.File::getName)
              .filter(name -> !name.endsWith("html"))
              .findAny().orElseThrow();
        assertThat(backupName, endsWith("bz2"));
    }

    @ParameterizedTest
    @MethodSource(BACKUP_REASON_VALUES_METHOD_SIGNATURE)
    void createBackupCreatesBackupWithReasonInFileName(BackupReason reason) throws Exception {
        final File file = new File();
        final java.io.File physicalFile = generateFile();
        file.setLabel(physicalFile.getName());
        document.addFile(file);
        file.setDocument(document);
        final java.io.File docDir = physicalFile.getParentFile();
        assertNotNull(docDir.listFiles());
        assertEquals(1, docDir.listFiles().length);
        sut.createBackup(file, reason);
        assertEquals(2, docDir.listFiles().length);
        String backupName = Arrays.stream(docDir.listFiles())
                .map(java.io.File::getName)
                .filter(name -> !name.endsWith(".html"))
                .map(name -> name.substring(0, name.lastIndexOf('.'))) // strip file format
                .findAny().orElseThrow();
        assertThat(backupName, endsWith(reason.name()));
    }

    @Test
    void getBackupReturnsBackupCreatedMostSoonAfterTimestamp() throws Exception {
        final File file = new File();
        final java.io.File physicalFile = generateFile();
        file.setLabel(physicalFile.getName());
        document.addFile(file);
        file.setDocument(document);

        final List<java.io.File> files = createTestBackups(file);
        final java.io.File expected = files.get(Generator.randomIndex(files));
        int separatorIndex = expected.getName().indexOf(DocumentFileUtils.BACKUP_NAME_SEPARATOR) + 1;
        final String strBackupTimestamp = expected.getName().substring(separatorIndex, separatorIndex +
                DocumentFileUtils.BACKUP_TIMESTAMP_LENGTH);
        final TemporalAccessor backupTimestamp = DocumentFileUtils.BACKUP_TIMESTAMP_FORMAT.parse(
                strBackupTimestamp);
        final Instant timestamp = Instant.from(backupTimestamp).minusSeconds(5);

        final BackupFile result = sut.getBackup(file, timestamp);
        assertEquals(expected, result.file());
    }

    @Test
    void getBackupReturnsOldestBackupWhenTimestampIsEpoch()  throws Exception {
        final File file = new File();
        final java.io.File physicalFile = generateFile();
        file.setLabel(physicalFile.getName());
        document.addFile(file);
        file.setDocument(document);

        final List<java.io.File> files = createTestBackups(file);
        final BackupFile result = sut.getBackup(file, Instant.EPOCH);
        assertEquals(files.get(0), result.file());
    }

    @Test
    void getBackupReturnsCurrentFileWhenTimestampIsNow() throws Exception {
        // Slightly back in time to prevent issues with test speed and instant comparison
        final Instant at = Utils.timestamp().minusMillis(100);
        final File file = new File();
        final java.io.File physicalFile = generateFile();
        file.setLabel(physicalFile.getName());
        document.addFile(file);
        file.setDocument(document);

        createTestBackups(file);
        final BackupFile result = sut.getBackup(file, at);
        assertEquals(physicalFile, result.file());
    }

    @Test
    void getBackupReturnsCurrentFileWhenTimestampIsInFuture() throws Exception {
        final Instant at = Utils.timestamp().plusSeconds(100);
        final File file = new File();
        final java.io.File physicalFile = generateFile();
        file.setLabel(physicalFile.getName());
        document.addFile(file);
        file.setDocument(document);

        createTestBackups(file);
        final BackupFile result = sut.getBackup(file, at);
        assertEquals(physicalFile, result.file());
    }

    @Test
    void getBackupThrowsNotFoundExceptionWhenParentDocumentDirectoryDoesNotExistOnFileSystem() {
        final File file = Generator.generateFileWithId("test.html");
        document.addFile(file);
        file.setDocument(document);
        assertThrows(NotFoundException.class, () -> sut.getBackup(file, Utils.timestamp()));
    }

    @Test
    void getBackupThrowsNotFoundExceptionWhenFileDoesNotExistInParentDocumentDirectoryOnFileSystem()
            throws Exception {
        final File file = new File();
        final java.io.File physicalFile = generateFile();
        file.setLabel(physicalFile.getName());
        document.addFile(file);
        file.setDocument(document);
        physicalFile.delete();

        assertThrows(NotFoundException.class, () -> sut.getBackup(file, Utils.timestamp()));
    }

    @Test
    void getBackupHandlesLegacyBackupWithoutBackupReason() throws Exception {
        final File file = new File();
        final java.io.File physicalFile = generateFile();
        file.setLabel(physicalFile.getName());
        document.addFile(file);
        file.setDocument(document);

        final Instant now = Instant.now().minusSeconds(100);

        final String path = physicalFile.getAbsolutePath();
        // Legacy pattern used multiple millis places
        final String newPath = path + DocumentFileUtils.BACKUP_NAME_SEPARATOR +
                DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss_S")
                                 .withZone(ZoneId.systemDefault())
                                 .format(now.plusSeconds(10));
        assertEquals(path, newPath.substring(0, path.length()));
        final java.io.File backup = new java.io.File(newPath);
        Files.copy(physicalFile.toPath(), backup.toPath());
        backup.deleteOnExit();
        final BackupFile result = sut.getBackup(file, now);
        assertEquals(backup, result.file());
    }

    @Test
    void getBackupHandlesLegacyBackupTimestampPattern() throws Exception {
        final File file = new File();
        final java.io.File physicalFile = generateFile();
        file.setLabel(physicalFile.getName());
        document.addFile(file);
        file.setDocument(document);

        final Instant now = Instant.now().minusSeconds(100);

        final String path = physicalFile.getAbsolutePath();
        // Legacy pattern used multiple millis places
        final String newPath = path + DocumentFileUtils.BACKUP_NAME_SEPARATOR +
                DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss_SSS")
                                 .withZone(ZoneId.systemDefault())
                                 .format(now.plusSeconds(10));
        final java.io.File backup = new java.io.File(newPath);
        Files.copy(physicalFile.toPath(), backup.toPath());
        backup.deleteOnExit();
        final BackupFile result = sut.getBackup(file, now);
        assertEquals(backup, result.file());
    }

    @Test
    void getBackupSkipsBackupFilesWithInvalidTimestampPattern() throws Exception {
        final File file = new File();
        final java.io.File physicalFile = generateFile();
        file.setLabel(physicalFile.getName());
        document.addFile(file);
        file.setDocument(document);

        final String path = physicalFile.getAbsolutePath();
        // Legacy pattern used multiple millis places
        final String newPath = path + DocumentFileUtils.BACKUP_NAME_SEPARATOR +
                DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss")
                                 .withZone(ZoneId.systemDefault())
                                 .format(Instant.now().minusSeconds(10));
        final java.io.File backup = new java.io.File(newPath);
        Files.copy(physicalFile.toPath(), backup.toPath());
        backup.deleteOnExit();
        final BackupFile result = sut.getBackup(file, Instant.EPOCH);
        assertEquals(physicalFile, result.file());
    }

    @Test
    void getBackupsWithNullReasonReturnsAllBackups() throws Exception {
        final int expectedBackupsPerReason = 3;
        final File file = new File();
        final java.io.File physicalFile = generateFile();
        file.setLabel(physicalFile.getName());
        document.addFile(file);
        file.setDocument(document);
        int expectedBackups = 0;
        // generate backups for all reasons
        for (BackupReason reason : BackupReason.values()) {
            createTestBackups(file, reason);
            expectedBackups += expectedBackupsPerReason; // the amount of created backups
        }

        List<BackupFile> backups = sut.getBackups(file, null);
        assertEquals(expectedBackups, backups.size());
    }

    @ParameterizedTest
    @MethodSource(BACKUP_REASON_VALUES_METHOD_SIGNATURE)
    void getBackupsReturnsOnlyBackupsWithGivenReason(BackupReason reasonFilter) throws Exception {
        final int expectedBackups = 3;
        final File file = new File();
        final java.io.File physicalFile = generateFile();
        file.setLabel(physicalFile.getName());
        document.addFile(file);
        file.setDocument(document);
        // generate backups for all reasons
        for (BackupReason reason : BackupReason.values()) {
            createTestBackups(file, reason);
        }

        List<BackupFile> backups = sut.getBackups(file, reasonFilter);
        assertEquals(expectedBackups, backups.size());

        for (BackupFile backup : backups) {
            assertEquals(reasonFilter, backup.backupReason());
        }
    }

    @Test
    void openBackupOpensCompressedBackup() throws Exception {
        final File file = new File();
        final java.io.File physicalFile = generateFile();
        file.setLabel(physicalFile.getName());
        document.addFile(file);
        file.setDocument(document);

        sut.createBackup(file, BackupReason.UNKNOWN);

        BackupFile backupFile = sut.getBackup(file, Instant.EPOCH);
        java.io.File extractedFile = sut.openBackup(backupFile);
        final List<String> backupLines = Files.readAllLines(extractedFile.toPath());
        final String result = String.join("\n", backupLines);
        assertEquals(CONTENT, result);
    }

    @Test
    void openBackupOpensLegacyUncompressedBackup() throws Exception {
        final File file = new File();
        final java.io.File physicalFile = generateFile();
        file.setLabel(physicalFile.getName());
        document.addFile(file);
        file.setDocument(document);

        BackupFile backupFile = new BackupFile(Instant.now(), physicalFile, BackupReason.UNKNOWN);
        java.io.File extractedFile = sut.openBackup(backupFile);
        final List<String> backupLines = Files.readAllLines(extractedFile.toPath());
        final String result = String.join("\n", backupLines);
        assertEquals(CONTENT, result);
    }

    @Test
    void getBackupReturnsBackupWithEqualTimestamp() {
        final File fileResource = new File();
        final java.io.File originalFile = generateFile();
        fileResource.setLabel(originalFile.getName());
        document.addFile(fileResource);
        fileResource.setDocument(document);
        fileResource.setModified(Utils.timestamp());

        // prepare a backup
        sut.createBackup(fileResource, BackupReason.UNKNOWN);

        final BackupFile backupFile = sut.getBackup(fileResource, Instant.EPOCH);
        final BackupFile sameBackup = sut.getBackup(fileResource, backupFile.timestamp());

        assertNotNull(backupFile);
        assertEquals(backupFile, sameBackup);
        assertTrue(BZip2Utils.isCompressedFileName(backupFile.file().getName()));
    }

    @Test
    void restoreBackupRestoresTheBackupContents() throws Exception {
        final File fileResource = new File();
        final java.io.File originalFile = generateFile();
        fileResource.setLabel(originalFile.getName());
        document.addFile(fileResource);
        fileResource.setDocument(document);
        fileResource.setModified(Utils.timestamp());

        final Instant backupTimestamp = Utils.timestamp().minusSeconds(1);
        // prepare a backup
        sut.createBackup(fileResource, BackupReason.UNKNOWN);

        BackupFile backupFile = sut.getBackup(fileResource, backupTimestamp);
        assertNotNull(backupFile);
        assertTrue(backupFile.file().isFile());
        assertTrue(BZip2Utils.isCompressedFileName(backupFile.file().getName()));

        // delete the original temporary file
        Files.delete(originalFile.toPath());
        assertFalse(originalFile.exists());

        sut.restoreBackup(fileResource, backupFile);
        assertTrue(originalFile.isFile());

        // check file contents
        final List<String> backupLines = Files.readAllLines(originalFile.toPath());
        final String result = String.join("\n", backupLines);
        assertEquals(CONTENT, result);
    }
}
